package skuber.model

import Model._

import java.util.Date

/**
 * @author David O'Riordan
 */
object Watch {
   type Event=Tuple2[EventType.EventType, ObjectResource]
  
  object EventType extends Enumeration {
    type EventType = Value
    val ADDED,MODIFIED,DELETED,ERROR = Value
  }
}