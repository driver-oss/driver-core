package xyz.driver.core

import java.io.File
import java.lang.management.ManagementFactory
import java.lang.reflect.Modifier

package stats {

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

    def operatingSystemStats: Map[String, String] = {
      val operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean
      operatingSystemMXBean.getClass.getDeclaredMethods
        .map(method => { method.setAccessible(true); method })
        .filter(method => method.getName.startsWith("get") && Modifier.isPublic(method.getModifiers))
        .map { method =>
          try {
            method.getName -> String.valueOf(method.invoke(operatingSystemMXBean))
          } catch {
            case t: Throwable => method.getName -> t.getMessage
        }
      } toMap
    }
  }
}
