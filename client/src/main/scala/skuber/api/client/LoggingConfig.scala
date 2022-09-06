package skuber.api.client

/**
  * @author David O'Riordan
  *
  */

import LoggingConfig.loggingEnabled
import scala.util.{Failure, Success, Try}

case class LoggingConfig(logConfiguration: Boolean=loggingEnabled("config", true), // outputs configuration on initialisation)
  logRequestBasic: Boolean=loggingEnabled("request", true), // logs method and URL for request
  logRequestBasicMetadata: Boolean=loggingEnabled("request.metadata", false), // logs key resource metadata information if available
  logRequestFullObjectResource: Boolean=loggingEnabled("request.object.full", false), // outputs full object resource if available
  logResponseBasic: Boolean=loggingEnabled("response", true), // logs basic response info (status code)
  logResponseBasicMetadata: Boolean=loggingEnabled("response.metadata", false), // logs some basic metadata from the returned resource, if available
  logResponseFullObjectResource: Boolean=loggingEnabled("response.object.full", false), // outputs full received object resource, if available
  logResponseListSize: Boolean=loggingEnabled("response.list.size", false), // logs size of any returned list resource
  logResponseListNames: Boolean=loggingEnabled("response.list.names", false), // logs list of names of items in any returned list resource
  logResponseFullListResource: Boolean= loggingEnabled("response.list.full", false) // outputs full contained object resources in list resources
)

object LoggingConfig {
  private def loggingEnabled(logEventType: String, fallback: Boolean) : Boolean= {
    sysProps.get(s"skuber.log.$logEventType").map {
      value => Try(value.toBoolean) match {
        case Success(value) => value
        case Failure(_) => throw new IllegalArgumentException(s"argument skuber.log.$logEventType:$value can't be parsed to boolean")
      }
    }.getOrElse(fallback)
  }
}

