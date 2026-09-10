package ${groupId}.configs;

import ${groupId}.MainApplication;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Activa la caché y decide en qué formato se guardan los valores.
 *
 * <p>Las dos cosas hacen falta. Sin {@code @EnableCaching}, las anotaciones {@code @Cacheable} y
 * {@code @CacheEvict} del código <b>no dan error: no hacen nada</b>. Todo funciona, simplemente sin
 * caché, y no hay manera de notarlo salvo mirando la latencia.</p>
 *
 * <p>Los parámetros de la caché (tiempo de vida, prefijo de clave, si se guardan los nulos) están en
 * {@code application.yaml}, que es donde se pueden cambiar por entorno. Aquí solo va lo que no se puede
 * expresar en configuración.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Guarda los valores como JSON en vez de con serialización de Java.
     *
     * <p>Es un cambio pequeño con tres consecuencias grandes:</p>
     * <ul>
     *   <li><b>Los records funcionan.</b> Por defecto Spring usa serialización de Java, que exige
     *       {@code Serializable}. Los DTOs de este proyecto son records y no lo implementan, así que
     *       cachear cualquiera de ellos fallaría en tiempo de ejecución.</li>
     *   <li><b>Se puede depurar.</b> Un {@code GET} sobre la clave en Redis devuelve algo legible, no un
     *       bloque binario.</li>
     *   <li><b>No ata la caché a una versión de las clases.</b> La serialización de Java guarda la firma
     *       de la clase: tocar un DTO invalida de golpe todo lo cacheado —o peor, revienta al leerlo.</li>
     * </ul>
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer valoresEnJson() {
        // ⚠️ Se usa un customizer, y NO un bean RedisCacheConfiguration, a propósito: declarar ese bean
        // hace que Spring Boot IGNORE por completo las propiedades `spring.cache.redis.*` del yaml
        // —tiempo de vida incluido— sin avisar de nada. El customizer se aplica DESPUÉS, así que parte de
        // la configuración ya construida a partir del yaml y solo le cambia el serializador.
        return builder -> builder.cacheDefaults(
                builder.cacheDefaults().serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializadorJson())));
    }

    private static GenericJacksonJsonRedisSerializer serializadorJson() {
        return GenericJacksonJsonRedisSerializer.builder()
                // Sin información de tipo, al leer de Redis vuelve un LinkedHashMap y el cast al DTO
                // revienta con «LinkedHashMap cannot be cast to ...». Con ella, el JSON lleva un campo
                // extra que dice qué clase reconstruir.
                .enableDefaultTyping(TIPOS_PERMITIDOS)
                // `cache-null-values: true` (application.yaml) hace que Spring guarde los nulos envueltos
                // en su propio marcador. Sin esto, ese marcador no sabe serializarse.
                .enableSpringCacheNullValueSupport()
                .build();
    }

    /**
     * Qué clases se aceptan al reconstruir un valor leído de Redis.
     *
     * <p>Existe una alternativa de una línea, {@code enableUnsafeDefaultTyping()}, y el nombre no es
     * decorativo: aceptar cualquier tipo convierte a quien pueda escribir en Redis en alguien que
     * ejecuta código en este proceso —es la familia de fallos de deserialización de toda la vida—. Como
     * lo único que se cachea aquí son DTOs propios, la lista blanca sale gratis.</p>
     *
     * <p>El paquete se deduce de {@code MainApplication} en vez de escribirse a mano, para que mover o
     * renombrar el paquete base no deje esto apuntando a un sitio que ya no existe.</p>
     */
    private static final PolymorphicTypeValidator TIPOS_PERMITIDOS = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType(MainApplication.class.getPackageName())
            .allowIfSubType("java.util.")
            .allowIfSubType("java.time.")
            .build();
}
