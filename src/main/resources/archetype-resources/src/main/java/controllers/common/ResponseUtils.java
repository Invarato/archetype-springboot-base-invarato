package ${groupId}.controllers.common;

import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;


public class ResponseUtils {

    private ResponseUtils() {
    }

    /**
     * Builds a ResponseEntity with status '201 Created'. A location header is generated
     * by appending the provided value to the current request URI, and the provided body
     * is used as the response body.
     *
     * @param <T>   The type of the response body.
     * @param value The value used to generate the location URI. This value will replace
     *              the placeholder in the URI path segment.
     * @param body  The body content to include in the response.
     * @return A ResponseEntity containing the provided body and a location header
     * pointing to the created resource.
     */
    public static <T> ResponseEntity<T> responseEntityCreated(Object value, T body) {
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest() // Takes current base URL
                .path("/{val}")       // Adds "/{val}" at the end 
                .buildAndExpand(value) // Replaces {val} with provided value
                .toUri();

        return ResponseEntity.created(location).body(body); // Returns the response
    }

    /**
     * Creates a ResponseEntity with status '201 Created'. The location header and body
     * are populated using the provided bodyValue for both the resource identifier
     * and response content.
     *
     * @param <T>       The type of the response body.
     * @param bodyValue The object used to populate the response body and identifier
     *                  for the location header.
     * @return A ResponseEntity containing the provided bodyValue in the response body
     * and a location header pointing to the created resource.
     */
    public static <T> ResponseEntity<T> responseEntityCreated(T bodyValue) {
        return responseEntityCreated(bodyValue, bodyValue);
    }

    /**
     * Creates a ResponseEntity with status '204 No Content'.
     *
     * @return A ResponseEntity object with HTTP status of NO_CONTENT.
     */
    public static ResponseEntity<Void> responseNoContent() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }


    /**
     * Creates a ResponseEntity with the status '201 Created', including a location header and body based on the provided AssigneeUuid.
     *
     * @param assigneeUuid The AssigneeUuid entity containing the UUID and type information to be included in the response.
     * @return A ResponseEntity containing the UuidResponse object in the body and a location header pointing to the created resource.
     */
//    public static ResponseEntity<UuidResponse> generateResponseCreated(AssigneeUuid assigneeUuid) {
//        UuidResponse uuidResponse = new UuidResponse(assigneeUuid.getAssigneeUuid(), assigneeUuid.getAssigneeType());
//        return ResponseUtils.responseEntityCreated(uuidResponse.assigneeUuid(), uuidResponse);
//    }

}