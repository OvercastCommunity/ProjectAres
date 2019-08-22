package tc.oc.commons.bukkit.report;

import com.google.common.collect.ImmutableList;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import tc.oc.commons.bukkit.chat.NameStyle;
import tc.oc.commons.bukkit.chat.PlayerComponent;
import tc.oc.commons.bukkit.commands.CommandUtils;
import tc.oc.commons.bukkit.nick.IdentityProvider;
import tc.oc.commons.core.chat.Component;
import tc.oc.commons.core.chat.Components;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class ReportCreator {

    private final IdentityProvider identityProvider;
    @Inject ReportCreator(IdentityProvider identityProvider) {
        this.identityProvider = identityProvider;
    }

    public List<? extends BaseComponent> createReport(String reporter, String reported, String reason) {
        final List<BaseComponent> parts = new ArrayList<>();

        parts.add(new Component(
                new Component("["),
                new Component("R", ChatColor.GOLD),
                new Component("]")
        ));

        if(reporter != null) {
            parts.add(new PlayerComponent(this.identityProvider.onlineIdentity(reporter), NameStyle.FANCY));
        } else {
            parts.add(CommandUtils.CONSOLE_COMPONENT_NAME);
        }

        parts.add(new Component("reports", ChatColor.YELLOW));

        parts.add(new Component(
                new PlayerComponent(this.identityProvider.onlineIdentity(reported), NameStyle.FANCY),
                new Component(": ", ChatColor.YELLOW)
        ));
        parts.add(new Component(reason, ChatColor.WHITE));

        return ImmutableList.of(Components.join(Components.space(), parts));
    }
}
