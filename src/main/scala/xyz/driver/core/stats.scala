package xyz.driver.core

import java.io.File
import java.lang.management.ManagementFactory

object stats {

  final case class MemoryStats(free: Long, total: Long, max: Long)

  final case class GarbageCollectorStats(totalGarbageCollections: Long, garbageCollectionTime: Long)

  final case class FileRootSpace(path: String, totalSpace: Long, freeSpace: Long, usableSpace: Long)

  object SystemStats {

    def memoryUsage: MemoryStats = {
      val runtime = Runtime.getRuntime
      MemoryStats(runtime.freeMemory, runtime.totalMemory, runtime.maxMemory)
    }

    def availableProcessors: Int = {
      Runtime.getRuntime.availableProcessors()
    }

    def garbageCollectorStats: GarbageCollectorStats = {
      import scala.collection.JavaConverters._

      val (totalGarbageCollections, garbageCollectionTime) =
        ManagementFactory.getGarbageCollectorMXBeans.asScala.foldLeft(0L -> 0L) {
          case ((total, collectionTime), gc) =>
            (total + math.max(0L, gc.getCollectionCount)) -> (collectionTime + math.max(0L, gc.getCollectionTime))
        }

      GarbageCollectorStats(totalGarbageCollections, garbageCollectionTime)
    }

    def fileSystemSpace: Array[FileRootSpace] = {
      File.listRoots() map { root =>
        FileRootSpace(root.getAbsolutePath, root.getTotalSpace, root.getFreeSpace, root.getUsableSpace)
      }
    }

    @deprecated(
      "OS stats accessed internal APIs which have been removed in new versions of Java. " +
        "Refer to InfluxDB and Grafana instead for OS metrics.",
      "1.11.9")
    def operatingSystemStats: Map[String, String] = Map.empty

  }
}
