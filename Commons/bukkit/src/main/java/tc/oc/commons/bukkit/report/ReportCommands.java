package tc.oc.commons.bukkit.report;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.time.Instant;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import tc.oc.api.bukkit.users.BukkitUserStore;
import tc.oc.api.docs.Report;
import tc.oc.api.docs.Server;
import tc.oc.commons.bukkit.channels.AdminChannel;
import tc.oc.commons.core.chat.Audience;
import tc.oc.minecraft.scheduler.SyncExecutor;
import tc.oc.api.model.QueryService;
import tc.oc.commons.bukkit.chat.Audiences;
import tc.oc.commons.bukkit.commands.UserFinder;
import tc.oc.commons.bukkit.nick.IdentityProvider;
import tc.oc.commons.core.chat.Component;
import tc.oc.commons.core.commands.CommandFutureCallback;
import tc.oc.commons.core.commands.Commands;
import tc.oc.commons.core.commands.TranslatableCommandException;
import tc.oc.commons.core.formatting.PeriodFormats;
import tc.oc.commons.core.util.Comparables;
import tc.oc.minecraft.api.event.Listener;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class ReportCommands implements Commands, Listener {

    private final QueryService<Report> reportService;
    private final ReportCreator reportCreator;
    private final ReportConfiguration reportConfiguration;
    private final UserFinder userFinder;
    private final SyncExecutor syncExecutor;
    private final Server localServer;
    private final BukkitUserStore userStore;
    private final Audiences audiences;
    private final IdentityProvider identities;
    private final AdminChannel adminChannel;
    private final Map<CommandSender, Instant> senderLastReport = new WeakHashMap<>();

    @Inject ReportCommands(QueryService<Report> reportService,
                           ReportCreator reportCreator,
                           ReportConfiguration reportConfiguration,
                           UserFinder userFinder,
                           SyncExecutor syncExecutor,
                           Server localServer,
                           BukkitUserStore userStore,
                           Audiences audiences,
                           IdentityProvider identities,
                           AdminChannel adminChannel) {
        this.reportService = reportService;
        this.reportCreator = reportCreator;
        this.reportConfiguration = reportConfiguration;
        this.userFinder = userFinder;
        this.syncExecutor = syncExecutor;
        this.localServer = localServer;
        this.userStore = userStore;
        this.audiences = audiences;
        this.identities = identities;
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

        // check for cooldown expiration
        if(!sender.hasPermission(ReportPermissions.COOLDOWN_EXEMPT)) {
            final Instant lastReportTime = senderLastReport.get(sender);
            if (lastReportTime != null) {
                final Duration timeLeft = reportConfiguration.cooldown().minus(Duration.between(lastReportTime, Instant.now()));
                if (Comparables.greaterThan(timeLeft, Duration.ZERO)) {
                    throw new TranslatableCommandException("command.report.cooldown", PeriodFormats.briefNaturalApproximate(timeLeft));
                }
            }
        }

        if (sender.getServer().getPlayerExact(args.getString(0), sender) == null) {
            throw new TranslatableCommandException("command.playerNotFound");
        }

        ReportCreator reportCreator = new ReportCreator(this.identities);
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
