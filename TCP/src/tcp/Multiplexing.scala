package tcp

import ip.NodeInterface
import tcputil.ConvertObject
import tcputil.PrintTCPSegment
import scala.actors.threadpool.Executors

class Multiplexing(nodeInterface: NodeInterface, tcp: TCP) extends Runnable {
  var done = true

  // val executors = Executors.newCachedThreadPool()
  val executors = Executors.newFixedThreadPool(tcp.DefaultThreads)

  def run() {
    //will repeat until the thread ends
    while (done) {
      val tuple = tcp.multiplexingBuff.bufferRead
      if (tuple != null) {
        if (tcp.TraceMulti) {
          println("Multiplexing Time: " + System.nanoTime)
          println("Multiplexing start")
          PrintTCPSegment.printBinary(ConvertObject.TCPSegmentToByte(tuple._3))
          println("Multiplexing end")
        }

        executors.execute(new SendTCPSegmentHandler(tuple, nodeInterface, tcp))
      }
    }
  }

  def cancel() {
    done = false
    executors.shutdown
  }
}