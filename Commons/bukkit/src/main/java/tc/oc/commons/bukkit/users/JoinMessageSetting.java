package tc.oc.commons.bukkit.users;

import me.anxuiz.settings.Setting;
import me.anxuiz.settings.SettingBuilder;
import me.anxuiz.settings.types.EnumType;
import me.anxuiz.settings.types.Name;
import tc.oc.commons.bukkit.nick.Familiarity;

public class JoinMessageSetting {
    private static final Setting inst = new SettingBuilder()
        .name("JoinMessages").alias("jms").alias("jm")
        .summary("Join messages displayed to you")
        .description("Options:\n" +
                     "ALL: all messages\n" +
                     "FRIENDS: messages from friends\n" +
                     "NONE: no messages\n")
        .type(new EnumType<>("Join Message Options", Options.class))
        .defaultValue(Options.ALL)
        .get();

    public static Setting get() {
        return inst;
    }

    public enum Options {
        @Name("all") ALL(Familiarity.PERSON),
        @Name("friends") FRIENDS(Familiarity.FRIEND),
        @Name("none") NONE(Familiarity.SELF);

        private final Familiarity minimumFamiliarity;

        Options(Familiarity minimumFamiliarity) {
            this.minimumFamiliarity = minimumFamiliarity;
        }

        public boolean isAllowed(Familiarity familiarity) {
            return familiarity.noLessThan(minimumFamiliarity);
        }
    }
}
