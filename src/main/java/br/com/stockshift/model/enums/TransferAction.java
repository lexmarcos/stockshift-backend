package br.com.stockshift.model.enums;

public enum TransferAction {
    CREATE(true, false),
    UPDATE(true, false),
    CANCEL(true, false),
    DISPATCH(true, false),
    START_VALIDATION(false, true),
    SCAN_ITEM(false, true),
    COMPLETE(false, true);

    private final boolean outboundAction;
    private final boolean inboundAction;

    TransferAction(boolean outboundAction, boolean inboundAction) {
        this.outboundAction = outboundAction;
        this.inboundAction = inboundAction;
    }

    public boolean isOutboundAction() {
        return outboundAction;
    }

    public boolean isInboundAction() {
        return inboundAction;
    }
}
