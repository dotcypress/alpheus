package alpheus

import chisel3._
import chisel3.util._

class PLLBlackBox extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle() {
    val RESET = Input(Bool())
    val REFERENCECLK = Input(Clock())
    val PLLOUTCORE = Output(Clock())
    val PLLOUTGLOBAL = Output(Clock())
  })
  setInline(
    "PLLBlackBox.v", s"""module PLLBlackBox(REFERENCECLK, PLLOUTCORE, PLLOUTGLOBAL, RESET);
    |  input REFERENCECLK;
    |  input RESET;
    |  output PLLOUTCORE;
    |  output PLLOUTGLOBAL;
    |  SB_PLL40_CORE ins(
    |    .REFERENCECLK(REFERENCECLK),
    |    .PLLOUTCORE(PLLOUTCORE),
    |    .PLLOUTGLOBAL(PLLOUTGLOBAL),
    |    .EXTFEEDBACK(),
    |    .DYNAMICDELAY(),
    |    .RESETB(RESET),
    |    .BYPASS(1'b0),
    |    .LATCHINPUTVALUE(),
    |    .LOCK(),
    |    .SDI(),
    |    .SDO(),
    |    .SCLK()
    |  );
    |  defparam ins.DIVR = 4'b0000;
    |  defparam ins.DIVF = 7'b0101111;
    |  defparam ins.DIVQ = 3'b011;
    |  defparam ins.FILTER_RANGE = 3'b001;
    |  defparam ins.FEEDBACK_PATH = "SIMPLE";
    |  defparam ins.DELAY_ADJUSTMENT_MODE_FEEDBACK = "FIXED";
    |  defparam ins.FDA_FEEDBACK = 4'b0000;
    |  defparam ins.DELAY_ADJUSTMENT_MODE_RELATIVE = "FIXED";
    |  defparam ins.FDA_RELATIVE = 4'b0000;
    |  defparam ins.SHIFTREG_DIV_MODE = 2'b00;
    |  defparam ins.PLLOUT_SELECT = "GENCLK";
    |endmodule""".stripMargin)
}
