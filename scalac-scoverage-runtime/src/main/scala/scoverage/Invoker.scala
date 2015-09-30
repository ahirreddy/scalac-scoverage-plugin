package scoverage

import java.lang.Runtime
import java.io.{FileFilter, File, FileWriter}
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import scala.collection.{mutable, Set}
import scala.collection.concurrent.TrieMap
import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.Random

/** @author Stephen Samuel */
object Invoker {

  private val MeasurementsPrefix = "scoverage.measurements."
  // Key: (Path to measurement directory, scoverage statement id)
  // Value: Doesn't matter, this map is used as a set
  private[this] val ids = new ConcurrentHashMap[(String, Int), Boolean]
  private[this] val JVM_ID = UUID.randomUUID()

  Runtime.getRuntime().addShutdownHook(new Thread() {
    override def run(): Unit = {
      for ((dataDir, id) <- ids.keySet().asScala) {
        val writer = new FileWriter(measurementFile(dataDir), true)
        writer.append(id.toString + '\n').flush()
        writer.close()
      }
      println(s"Finished writing measurement files. JVM_ID: $JVM_ID")
    }
  })

  /**
   * We record that the given id has been invoked by appending its id to the coverage
   * data file.
   *
   * This will happen concurrently on as many threads as the application is using,
   * so we use one file per thread, named for the thread id.
   *
   * This method is not thread-safe if the threads are in different JVMs, because
   * the thread IDs may collide.
   * You may not use `scoverage` on multiple processes in parallel without risking
   * corruption of the measurement file.
   *
   * @param id the id of the statement that was invoked
   * @param dataDir the directory where the measurement data is held
   */
  final def invoked(id: Int, dataDir: String): Unit = {
    ids.putIfAbsent((dataDir, id), true)
  }

  def measurementFile(dataDir: File): File = measurementFile(dataDir.getAbsolutePath)
  def measurementFile(dataDir: String): File = new File(dataDir, MeasurementsPrefix + JVM_ID)

  def findMeasurementFiles(dataDir: String): Array[File] = findMeasurementFiles(new File(dataDir))
  def findMeasurementFiles(dataDir: File): Array[File] = dataDir.listFiles(new FileFilter {
    override def accept(pathname: File): Boolean = pathname.getName.startsWith(MeasurementsPrefix)
  })

  // loads all the invoked statement ids from the given files
  def invoked(files: Seq[File]): Set[Int] = {
    val acc = mutable.Set[Int]()
    files.foreach { file =>
      val reader = Source.fromFile(file)
      for ( line <- reader.getLines() ) {
        if (!line.isEmpty) {
          acc += line.toInt
        }
      }
      reader.close()
    }
    acc
  }
}
