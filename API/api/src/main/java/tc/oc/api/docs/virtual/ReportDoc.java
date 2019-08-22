package tc.oc.api.docs.virtual;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import tc.oc.api.annotations.Serialize;
import tc.oc.api.docs.PlayerId;

public interface ReportDoc {
    interface Partial extends PartialModel {}

    @Serialize
    interface Base extends Model, Partial {
        @Nonnull String reason();
    }

    @Serialize
    interface Creation extends Base {
        @Nullable String reporter_id();
        @Nonnull String reported_id();
    }

    @Serialize
    interface Complete extends Base {
        @Nullable PlayerId reporter();
        @Nullable PlayerId reported();
    }
}
