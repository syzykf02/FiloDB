package filodb.coordinator

import akka.actor.ActorSystem
import com.typesafe.config.Config

import filodb.core.store.{ColumnStore, MetaStore}
import filodb.core.FutureUtils
import filodb.core.reprojector._

/**
 * A trait to make setup of the [[NodeCoordinatorActor]] stack a bit easier.
 * Mixed in for tests as well as the main FiloDB app and anywhere else the stack needs to be spun up.
 */
trait CoordinatorSetup {
  def system: ActorSystem

  // The global Filo configuration object.  Should be ConfigFactory.load.getConfig("filodb")
  def config: Config

  // implicit lazy val ec = FutureUtils.getBoundedExecContext(config.getInt("max-reprojection-futures"),
  //                                                     "filodb.core",
  //                                                     config.getInt("core-futures-pool-size"))
  // lazy val readEc = FutureUtils.getBoundedExecContext(256, "filodb.query",
  //                                                config.getInt("queries-futures-pool-size"))

  // NOTE: Using getBoundedExecContext above was leading to deadlocks.  I believe this is because
  // ColumnStore.appendSegment calls out to 4 or 5 other futures, including getting the tables, cached
  // indexes, writing to both chunkMap as well as chunks tables, and saving cached segments.  Thus it would
  // be deadlocked blocked waiting for space on the queue for one of these things, while the original future
  // is waiting for it to get unblocked.
  // Using the global context is not great, but at least doesn't deadlock.
  // In the future: implement backpressure in the reprojector... at a higher level.  However, these is
  // already backpressure in DatasetCoordinatorActor.
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  val readEc = ec

  // These should be implemented as lazy val's, though tests might want to reset them
  val columnStore: ColumnStore
  val metaStore: MetaStore
  lazy val reprojector = new DefaultReprojector(config, columnStore)

  // TODO: consider having a root actor supervising everything
  lazy val coordinatorActor =
    system.actorOf(NodeCoordinatorActor.props(metaStore, reprojector, columnStore, config),
                   "coordinator")

  def shutdown(): Unit = {
    system.shutdown()
    columnStore.shutdown()
    metaStore.shutdown()
  }
}
