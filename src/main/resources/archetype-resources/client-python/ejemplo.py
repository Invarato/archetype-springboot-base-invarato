"""
Como se usa este cliente. Copiable tal cual.

Para verlo funcionar contra el servicio de verdad:

    make docker-up                  # Postgres y Redis
    make run                        # la aplicacion en el 8080
    make token                      # un JWT de desarrollo, valido una hora

    ./mvnw -pl client-python package         # genera el paquete Python
    pip install -e client-python/target/generated-sources/python
    API_TOKEN=EL_TOKEN python client-python/ejemplo.py

El paquete se genera en target/ y NO se versiona: la fuente de verdad es el contrato
(contract/src/main/resources/openapi/openapi.json), igual que para el cliente Java.
"""

import os
import sys

import api_client


def api(url_base: str, token: str) -> "api_client.ExampleControllerApi":
    """Construye el cliente apuntando a un servidor y autenticado con un token.

    Estas tres lineas son lo que cuesta encontrar la primera vez. `access_token` es lo que hace que
    cada peticion salga con la cabecera `Authorization: Bearer ...`.
    """
    configuracion = api_client.Configuration(host=url_base, access_token=token)
    return api_client.ExampleControllerApi(api_client.ApiClient(configuracion))


def main() -> int:
    url_base = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
    token = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("API_TOKEN", "")

    if not token:
        # Se falla pronto y diciendo que hacer: sin token la primera llamada daria un 401 pelado.
        print(
            "Falta el token. Sacalo con `make token` y pasalo como segundo argumento "
            "o en la variable de entorno API_TOKEN.",
            file=sys.stderr,
        )
        return 1

    cliente = api(url_base, token)

    print("GET  /hello       ->", cliente.say_hello())

    nuevo_id = cliente.create(
        api_client.MyTableRequest(
            name="Ejemplo",
            surname="Creado desde el cliente",
            description="Generado a partir del contrato OpenAPI",
        )
    )
    print("POST /examples    -> id", nuevo_id)

    creado = cliente.get_one(nuevo_id)
    print(f"GET  /examples/{nuevo_id} -> {creado.name} / {creado.surname}")

    todos = cliente.list_all()
    print("GET  /examples    ->", len(todos), "registros")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
