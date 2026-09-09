package ${groupId}.common;


import com.fasterxml.jackson.databind.ObjectMapper;


public abstract class UtilsTest {


    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Converts the given object to its JSON string representation.
     *
     * @param obj the object to serialize to JSON
     * @return the JSON string representation of the object
     * @throws RuntimeException if an error occurs during JSON serialization
     */
    public static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Error while serializing to JSON", e);
        }
    }


}
