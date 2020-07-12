package alpheus

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class TagValueIO(tagBits: Int) extends Bundle {
  require(tagBits > 0 && tagBits < 8, "Invalid tag width")

  val lenBits = 8 - tagBits
  val maxLen = (1 << lenBits) - 1
  val valBits = maxLen * 8

  val tag = Output(UInt(tagBits.W))
  val len = Output(UInt(lenBits.W))
  val value = Output(UInt(valBits.W))

  override def cloneType = (new TagValueIO(tagBits)).asInstanceOf[this.type]
}

class MTLVIO(tagBits: Int) extends Bundle {
  val rx = Input(UInt(1.W))
  val tx = Output(UInt(1.W))

  val in = Flipped(Decoupled(new TagValueIO(tagBits)))
  val out = Decoupled(new TagValueIO(tagBits))

  override def cloneType = (new MTLVIO(tagBits)).asInstanceOf[this.type]
}

object MTLV {
  object TxState extends ChiselEnum {
    val Ready, Preamble, Transmit = Value
  }

  object RxState extends ChiselEnum {
    val Waiting, Receive, Drain = Value
  }
}

class MTLV(frequency: Int, baudRate: Int, tagBits: Int) extends Module {
  val io = IO(new MTLVIO(tagBits))

  import MTLV.RxState
  val rx = Module(new Rx(frequency, baudRate))
  val rxState = RegInit(RxState.Waiting)
  val rxTag = RegInit(0.U(tagBits.W))
  val rxLength = RegInit(0.U(io.out.bits.lenBits.W))
  val rxValue = RegInit(VecInit(Seq.fill(io.out.bits.maxLen)(0.U(8.W))))
  val rxCounter = RegInit(0.U(io.out.bits.lenBits.W))

  rx.io.rxd := io.rx
  rx.io.channel.ready := rxState =/= RxState.Drain

  io.out.bits.tag := rxTag
  io.out.bits.len := rxLength
  io.out.bits.value := rxValue.asUInt
  io.out.valid := false.B

  switch(rxState) {
    is(RxState.Waiting) {
      when(rx.io.channel.valid) {
        val tag = rx.io.channel.bits.tail(tagBits)
        val valueLen = rx.io.channel.bits.head(tagBits)
        rxState := Mux(valueLen === 0.U, RxState.Drain, RxState.Receive)
        rxTag := tag
        rxLength := valueLen
        rxCounter := 0.U
      }
    }
    is(RxState.Receive) {
      when(rx.io.channel.valid) {
        rxState := Mux(
          rxLength === (rxCounter + 1.U),
          RxState.Drain,
          RxState.Receive
        )
        rxValue(rxCounter) := rx.io.channel.bits
        rxCounter := rxCounter + 1.U
      }
    }
    is(RxState.Drain) {
      when(io.out.ready) {
        rxState := RxState.Waiting
        io.out.valid := true.B
      }
    }
  }

  import MTLV.TxState
  val tx = Module(new Tx(frequency, baudRate))
  val txState = RegInit(TxState.Ready)
  val txTag = RegInit(0.U(tagBits.W))
  val txLength = RegInit(0.U(io.in.bits.lenBits.W))
  val txValue = RegInit(VecInit(Seq.fill(io.out.bits.maxLen)(0.U(8.W))))
  val txCounter = RegInit(0.U(io.in.bits.lenBits.W))

  tx.io.channel.bits := 0.U
  tx.io.channel.valid := false.B

  io.tx := tx.io.txd
  io.in.ready := txState === TxState.Ready

  switch(txState) {
    is(TxState.Ready) {
      when(io.in.valid) {
        txState := TxState.Preamble
        txTag := io.in.bits.tag
        txLength := io.in.bits.len
        txCounter := 0.U
        for (i <- 0 until io.out.bits.maxLen) {
          txValue(i) := io.in.bits.value.tail(i * 8).head(8)
        }
      }
    }
    is(TxState.Preamble) {
      when(tx.io.channel.ready) {
        txState := Mux(txLength === 0.U, TxState.Ready, TxState.Transmit)
        tx.io.channel.bits := Cat(txLength, txTag)
        tx.io.channel.valid := true.B
      }
    }
    is(TxState.Transmit) {
      when(tx.io.channel.ready) {
        txState := Mux(
          txLength === (txCounter + 1.U),
          TxState.Ready,
          TxState.Transmit
        )
        tx.io.channel.bits := txValue(txCounter)
        tx.io.channel.valid := true.B
        txCounter := txCounter + 1.U
      }
    }
  }
}
