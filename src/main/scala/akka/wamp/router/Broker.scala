package akka.wamp.router

import akka.actor._
import akka.wamp.client.Client
import akka.wamp._
import akka.wamp.messages._

/**
  * The broker routes [[Event]]s incoming from [[Client]]s with [[Roles.publisher]] to 
  * [[Client]]s with [[Roles.subscriber]] that are [[Subscribed]] to respective [[Topic]]s
  */
trait Broker { this: Router =>

  /** Map of subscriptions. Each entry is for one topic only and it can have one or many subscribers */
  private[router] var subscriptions = Map.empty[Id, Subscription]
  
  /** Set of publication identifiers */
  private[router] var publications = Set.empty[Id]


  /** Handle publications lifecycle messages such as: PUBLISH */
  private[router] def handlePublications: Receive = {
    case message @ Publish(requestId, options, topic, payload) =>
      ifSessionOpen(message) { session =>
        val publisher = session.client
        val ack = options.get("acknowledge") == Some(true)
        if (session.roles.contains(Roles.publisher)) {
          val publicationId = scopes('global).nextId(excludes = publications)
          /**
            * By default, publications are unacknowledged, and the Broker will
            * not respond, whether the publication was successful indeed or not.
            * This behavior can be changed with the option
            *
            * "PUBLISH.Options.acknowledge|bool"
            */
          subscriptions.values.toList.filter(_.topic == topic) match {
            case Nil =>
              /**
                * Actually, no subscribers has subscribed to the given topic.
                */
              if (ack) {
                publisher ! Published(requestId, publicationId)
              }
            case subscription :: Nil =>
              /**
                * When a publication is successful and a Broker dispatches the event,
                * it determines a list of receivers for the event based on subscribers
                * for the topic published to and, possibly, other information in the event.
                *
                * Note that the publisher of an event will never receive the published 
                * event even if the publisher is also a subscriber of the topic published to.
                */
              subscription.subscribers.filter(_ != publisher).foreach { subscriber =>
                publications += publicationId
                subscriber ! Event(subscription.id, publicationId, Dict(), payload)
              }
              if (ack) {
                publisher ! Published(requestId, publicationId)
              }
              
            case _ => 
              throw new IllegalStateException()
          }
        }
        else {
          if (ack) {
            publisher ! Error(Publish.tpe, requestId, details = Error.defaultDetails, "akka.wamp.error.no_publisher_role")
          }
        }
      }
  }

  /**
    * Handle subscriptions lifecycle messages such as: 
    * SUBSCRIBE and UNSUBSCRIBE
    */
  private[router] def handleSubscriptions: Receive = {
    case message @ Subscribe(requestId, options, topic) =>
      ifSessionOpen(message) { session =>
        if (session.roles.contains(Roles.subscriber)) {
          subscriptions.values.toList.filter(_.topic == topic) match {
            case Nil => {
              /**
                * No subscribers have subscribed to the given topic yet.
                */
              val subscriptionId = scopes('router).nextId(excludes = subscriptions.keySet)
              subscriptions += (subscriptionId -> new Subscription(subscriptionId, Set(session.client), topic))
              session.client ! Subscribed(requestId, subscriptionId)
            }
            case subscription :: Nil => {
              if (!subscription.subscribers.contains(session.client)) {
                /**
                  * In case of receiving a SUBSCRIBE message from a client to the 
                  * topic already subscribed by others, broker should update the 
                  * subscribers set of the existing subscription and answer with 
                  * SUBSCRIBED message, containing the existing subscription ID. 
                  */
                subscriptions += (subscription.id -> subscription.copy(subscribers = subscription.subscribers + session.client))
              }
              else {
                /**
                  * In case of receiving a SUBSCRIBE message from the same subscriber 
                  * to already subscribed topic, broker should answer with 
                  * SUBSCRIBED message, containing the existing subscription ID.
                  */
              }
              session.client ! Subscribed(requestId, subscription.id)
            }
            case _ => {
              log.warning("[{}] !!! IllegalStateException: more than one subscription for topic {} found.", self.path.name, topic)
            }
          }
        }
        else {
          session.client ! Error(Subscribe.tpe, requestId, details = Error.defaultDetails, "akka.wamp.error.no_subscriber_role")
        }
      }

    case message @ Unsubscribe(requestId, subscriptionId) =>
      ifSessionOpen(message) { session =>
        subscriptions.get(subscriptionId) match {
          case Some(subscription) =>
            unsubscribe(session.client, subscription)
            session.client ! Unsubscribed(requestId)
          case None =>
            session.client ! Error(Unsubscribe.tpe, requestId, Error.defaultDetails, "wamp.error.no_such_subscription")
        }
      }
  }


  /**
    * Remove the given client from the given subscription. If no clients are
    * left then the given subscription will be also removed.
    * 
    * @param client is the client actor reference
    * @param subscription is the subscription the client has to be removed from
    */
  private[router] def unsubscribe(client: ActorRef, subscription: Subscription) = {
    if (subscription.subscribers.contains(client)) {
      if (subscription.subscribers.size == 1) {
        subscriptions -= subscription.id
      } else {
        subscriptions += (subscription.id -> subscription.copy(subscribers = subscription.subscribers - client))
      }
    }
  }

}

