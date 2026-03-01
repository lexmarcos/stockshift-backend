package br.com.stockshift.model.enums;

public enum StockMovementType {
  USAGE(MovementDirection.OUT),
  GIFT(MovementDirection.OUT),
  LOSS(MovementDirection.OUT),
  DAMAGE(MovementDirection.OUT),
  PURCHASE_IN(MovementDirection.IN),
  ADJUSTMENT_IN(MovementDirection.IN),
  ADJUSTMENT_OUT(MovementDirection.OUT),
  TRANSFER_IN(MovementDirection.IN),
  TRANSFER_OUT(MovementDirection.OUT);

  private final MovementDirection direction;

  StockMovementType(MovementDirection direction) {
    this.direction = direction;
  }

  public MovementDirection getDirection() {
    return direction;
  }

  public boolean isDebit() {
    return direction == MovementDirection.OUT;
  }

  public boolean isCredit() {
    return direction == MovementDirection.IN;
  }
}
