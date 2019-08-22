package tc.oc.commons.bukkit.report;

import java.util.Map;
import java.util.WeakHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.command.CommandSender;
import java.time.Duration;
import java.time.Instant;

import tc.oc.commons.bukkit.channels.AdminChannel;
import tc.oc.commons.bukkit.chat.Audiences;
import tc.oc.commons.core.chat.Component;
import tc.oc.commons.core.commands.Commands;
import tc.oc.commons.core.commands.TranslatableCommandException;
import tc.oc.commons.core.formatting.PeriodFormats;
import tc.oc.commons.core.util.Comparables;
import tc.oc.minecraft.api.event.Listener;

@Singleton
public class ReportCommands implements Commands, Listener {

    private final ReportCreator reportCreator;
    private final ReportConfiguration reportConfiguration;
    private final Audiences audiences;
    private final AdminChannel adminChannel;
    private final Map<CommandSender, Instant> senderLastReport = new WeakHashMap<>();

    @Inject ReportCommands(ReportCreator reportCreator,
                           ReportConfiguration reportConfiguration,
                           Audiences audiences,
                           AdminChannel adminChannel) {
        this.reportCreator = reportCreator;
        this.reportConfiguration = reportConfiguration;
        this.audiences = audiences;
        this.adminChannel = adminChannel;
    }

    private void assertEnabled() throws CommandException {
        if(!reportConfiguration.enabled()) {
            throw new TranslatableCommandException("command.reports.notEnabled");
        }
    }

    @Command(
        aliases = { "report" },
        usage = "<player> <reason>",
        desc = "Report a player who is breaking the rules",
        min = 2,
        max = -1
    )
    @CommandPermissions(ReportPermissions.CREATE)
    public void report(final CommandContext args, final CommandSender sender) throws CommandException {
        assertEnabled();

        // Check for cooldown expiration
        if(!sender.hasPermission(ReportPermissions.COOLDOWN_EXEMPT)) {
            final Instant lastReportTime = senderLastReport.get(sender);
            if (lastReportTime != null) {
                final Duration timeLeft = reportConfiguration.cooldown().minus(Duration.between(lastReportTime, Instant.now()));
                if (Comparables.greaterThan(timeLeft, Duration.ZERO)) {
                    throw new TranslatableCommandException("command.report.cooldown", PeriodFormats.briefNaturalApproximate(timeLeft));
                }
            }
        }

        // Check if player online
        if (sender.getServer().getPlayerExact(args.getString(0), sender) == null) {
            throw new TranslatableCommandException("command.playerNotFound");
        }

        // Send report to staff
        adminChannel.viewers()
                .filter(viewer ->
                        viewer.hasPermission(ReportPermissions.RECEIVE))
                .forEach(viewer ->
                        audiences.get(viewer).sendMessages(
                                reportCreator.createReport(sender.getName(),
                                        args.getString(0),
                                        args.getJoinedStrings(1))));


        audiences.get(sender).sendMessage(
                new Component(
                        new Component(new TranslatableComponent("misc.thankYou"), ChatColor.GREEN),
                        new Component(" "),
                        new Component(new TranslatableComponent("command.report.successful.dealtWithMessage"), ChatColor.GOLD)
                )
        );
    }
}
