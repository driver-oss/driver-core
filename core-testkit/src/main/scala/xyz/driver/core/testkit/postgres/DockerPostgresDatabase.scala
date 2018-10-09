package xyz.driver.core.testkit
package postgres

import xyz.driver.core.make

trait DockerPostgresDatabase {
  import com.spotify.docker.client._
  import com.spotify.docker.client.messages._

  lazy val dockerClient: DockerClient = DefaultDockerClient.fromEnv().build()

  val postgresVersion: String = "9.6"

  def setupDockerDatabase(
      username: String = "postgres",
      password: String = "postgres",
      database: String = "postgres",
      hostPort: Int = 15432): String = {
    import collection.JavaConverters._

    dockerClient.pull(s"postgres:$postgresVersion")

    val portBindings: Map[String, List[PortBinding]] = Map("5432" -> List(PortBinding.of("0.0.0.0", hostPort)))
    val portBindingsJava                             = portBindings.mapValues(_.asJava).asJava
    val hostConfig                                   = HostConfig.builder().portBindings(portBindingsJava).build()
    val containerConfig =
      ContainerConfig
        .builder()
        .hostConfig(hostConfig)
        .image(s"postgres:$postgresVersion")
        .exposedPorts("5432")
        .env(
          s"POSTGRES_USER=$username",
          s"POSTGRES_DB=$database",
          s"POSTGRES_PASSWORD=$password"
        )
        .build()

    make(dockerClient.createContainer(containerConfig).id())(dockerClient.startContainer)
  }

  def killDockerDatabase(containerId: String): Unit = {
    dockerClient.killContainer(containerId)
    dockerClient.removeContainer(containerId)
  }
}
