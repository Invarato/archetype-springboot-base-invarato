/*
 * Se ejecuta al terminar `archetype:generate`, sobre el proyecto ya escrito en disco.
 *
 * Por que existe: los arquetipos Maven NO conservan el bit de ejecucion de los ficheros. `mvnw` se genera
 * con permisos 644, asi que el primer `./mvnw` del proyecto recien creado responde "Permission denied" — un
 * arranque en falso justo en el comando que documenta el readme.
 *
 * `request` lo inyecta el propio maven-archetype-plugin.
 */

import java.nio.file.Files
import java.nio.file.Paths

def projectDir = Paths.get(request.outputDirectory, request.artifactId)

['mvnw'].each { nombre ->
    def f = projectDir.resolve(nombre)
    if (Files.exists(f)) {
        f.toFile().setExecutable(true, false)
        println "[archetype] +x ${nombre}"
    }
}
