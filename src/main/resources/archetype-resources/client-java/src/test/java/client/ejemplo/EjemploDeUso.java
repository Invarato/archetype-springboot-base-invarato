package ${groupId}.client.ejemplo;

import ${groupId}.client.ApiClient;
import ${groupId}.client.api.ExampleControllerApi;
import ${groupId}.client.model.MyTableRequest;
import ${groupId}.client.model.MyTableResponse;

import java.util.List;
import java.util.function.Supplier;

/**
 * Cómo se usa este cliente. Copiable tal cual.
 *
 * <p>Vive en <b>src/test</b> a propósito: así no viaja dentro del jar que se publica. Quien consuma el
 * cliente no quiere un ejemplo dentro de su classpath — pero sí quiere poder leerlo.</p>
 *
 * <p>Para verlo funcionar contra el servicio de verdad:</p>
 *
 * <pre>
 *   make docker-up          # Postgres y Redis
 *   make run                # la aplicación en el 8080
 *   make token              # un JWT de desarrollo, válido una hora
 *
 *   ./mvnw -pl client-java test-compile exec:java \
 *       -Dexec.classpathScope=test \
 *       -Dexec.mainClass="${groupId}.client.ejemplo.EjemploDeUso" \
 *       -Dexec.args="http://localhost:8080 EL_TOKEN"
 * </pre>
 *
 * <p>Y lo que de verdad hay que copiar es {@link #api(String, Supplier)}: son las cuatro líneas que
 * cuesta encontrar la primera vez.</p>
 */
public final class EjemploDeUso {

    private EjemploDeUso() {
    }

    /**
     * Construye el cliente apuntando a un servidor y autenticado con un token.
     *
     * <p>El token se pasa como {@link Supplier} y no como {@code String} porque un JWT <b>caduca</b>. Con
     * un valor fijo, un proceso de larga duración funciona hasta que expira y a partir de ahí devuelve
     * 401 para siempre; con un proveedor, cada petición pregunta por el token vigente y el día que se
     * añada renovación automática no hay que tocar nada de esto.</p>
     *
     * @param urlBase        raíz del servicio, sin barra final (por ejemplo {@code http://localhost:8080})
     * @param proveedorToken de dónde sale el JWT en cada petición
     * @return el API listo para llamar
     */
    public static ExampleControllerApi api(String urlBase, Supplier<String> proveedorToken) {
        ApiClient clienteHttp = new ApiClient();
        clienteHttp.setBasePath(urlBase);
        clienteHttp.setBearerToken(proveedorToken);
        return new ExampleControllerApi(clienteHttp);
    }

    public static void main(String[] args) {
        String urlBase = args.length > 0 ? args[0] : "http://localhost:8080";
        String token = args.length > 1 ? args[1] : System.getenv("API_TOKEN");

        if (token == null || token.isBlank()) {
            // Se falla pronto y diciendo qué hacer: sin token la primera llamada daría un 401 pelado.
            System.err.println("Falta el token. Sacalo con `make token` y pasalo como segundo argumento "
                    + "o en la variable de entorno API_TOKEN.");
            System.exit(1);
        }

        ExampleControllerApi api = api(urlBase, () -> token);

        // System.out en un ejemplo de linea de comandos es correcto: la salida ES el resultado. En el
        // codigo del servicio no lo es, y alli lo vigila ArchitectureTest.
        System.out.println("GET /hello        -> " + api.sayHello());

        Long id = api.create(new MyTableRequest()
                .name("Ejemplo")
                .surname("Creado desde el cliente")
                .description("Generado a partir del contrato OpenAPI"));
        System.out.println("POST /examples    -> id " + id);

        MyTableResponse creado = api.getOne(id);
        System.out.println("GET /examples/" + id + " -> " + creado.getName() + " / " + creado.getSurname());

        List<MyTableResponse> todos = api.listAll();
        System.out.println("GET /examples     -> " + todos.size() + " registros");
    }
}
