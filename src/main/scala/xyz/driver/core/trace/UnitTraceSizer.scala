package xyz.driver.core.trace
import com.google.cloud.trace.v1.util.Sizer
import com.google.devtools.cloudtrace.v1.Trace

class UnitTraceSizer extends Sizer[Trace] {
  override def size(sizeable: Trace) = 1
}
