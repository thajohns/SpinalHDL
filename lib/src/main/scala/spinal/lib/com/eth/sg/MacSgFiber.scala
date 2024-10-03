package spinal.lib.com.eth.sg

import spinal.core._
import spinal.core.fiber.Fiber
import spinal.lib.misc.InterruptNode
import spinal.lib.bus.tilelink
import spinal.lib._
import spinal.lib.bus.tilelink.BusParameter
import spinal.lib.com.eth.{Gmii, PhyParameter}
import spinal.lib.stringPimped
import spinal.lib.system.dma.sg2
import spinal.lib.system.dma.sg2.{DmaSgReadOnly, DmaSgReadOnlyParam}

import scala.collection.mutable.ArrayBuffer

case class MacSgFiberSpec(name : String,
                          ctrlAddress : BigInt,
                          txInterruptId : Int,
                          rxInterruptId : Int,
                          phyParam: PhyParameter,
                          txDmaParam : sg2.DmaSgReadOnlyParam)

object MacSgFiberSpec{
  def addOption(parser: scopt.OptionParser[Unit], specs: ArrayBuffer[MacSgFiberSpec]): Unit = {
    import parser._
    opt[Map[String, String]]("mac-sg").unbounded().action { (v, c) =>
      specs += MacSgFiberSpec(
        name = v("name"),
        ctrlAddress = v("address").toBigInt,
        txInterruptId = v("txIrq").toInt,
        rxInterruptId = v("rxIrq").toInt,
        phyParam = PhyParameter(
          txDataWidth = 8,
          rxDataWidth = 8
        ),
        txDmaParam = DmaSgReadOnlyParam(
          addressWidth = 32,
          dataWidth = 0,
          blockSize = 64,
          bufferBytes = 4096,
          pendingSlots = 2
        )
      )
    } text (s"")
  }
}

case class MacSgFiber(val p: MacSgParam,
                      val txCd : ClockDomain,
                      val rxCd : ClockDomain) extends Area{
  val ctrl = tilelink.fabric.Node.slave()
  val txMem = tilelink.fabric.Node.master()
  val txInterrupt = InterruptNode.master()

  val logic = Fiber build new Area{
    txMem.m2s.forceParameters(p.txDmaParam.getM2sParameter(txMem))
    txMem.s2m.unsupported()

    ctrl.m2s.supported.load(MacSg.getCtrlSupport(ctrl.m2s.proposed))
    ctrl.s2m.none()

    val core = new MacSg(
      p = p,
      ctrlParam = ctrl.bus.p,
      txMemParam = txMem.bus.p,
      ctrlCd = ClockDomain.current,
      txCd = txCd,
      rxCd = rxCd
    )

    core.io.ctrl <> ctrl.bus
    core.io.txMem <> txMem.bus
    txInterrupt.flag := core.io.interrupt

    val phy = master(Gmii())
    val phyTxFeed = txCd on phy.tx.fromTxStream()
    phyTxFeed.input << core.io.phy.tx
  }
}