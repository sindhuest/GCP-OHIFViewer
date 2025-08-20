package org.ohif.viewer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

@RestController
@RequestMapping("/dicomweb")
@CrossOrigin(origins = "*")
@Slf4j
public class DicomController {

    private final WebClient awsWebClient;
    private final ObjectMapper objectMapper;

    public DicomController(WebClient awsWebClient, ObjectMapper objectMapper) {
        this.awsWebClient = awsWebClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/studies")
    public Mono<ResponseEntity<String>> getStudies(@RequestParam(value = "limit", defaultValue = "100") int limit,
                                                  @RequestParam(value = "offset", defaultValue = "0") int offset,
                                                  @RequestParam(value = "fuzzymatching", defaultValue = "false") boolean fuzzymatching,
                                                  @RequestParam(value = "includefield", required = false) String includeField) {
        log.info("Fetching studies list with limit={}, offset={}, fuzzymatching={}, includefield={}",
                 limit, offset, fuzzymatching, includeField);

        return awsWebClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path(baseUrl() + "/studies");
                uriBuilder.queryParam("limit", limit);
                uriBuilder.queryParam("offset", offset);
                uriBuilder.queryParam("fuzzymatching", fuzzymatching);
                if (includeField != null && !includeField.isBlank()) {
                    uriBuilder.queryParam("includefield", includeField);
                }
                return uriBuilder.build();
            })
            .header(HttpHeaders.ACCEPT, "application/dicom+json")
            .retrieve()
            .bodyToMono(String.class)
            .map(data -> ResponseEntity.ok()
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .header("Cross-Origin-Embedder-Policy", "require-corp")
                .header("Cross-Origin-Opener-Policy", "same-origin")
                .contentType(MediaType.valueOf("application/dicom+json"))
                .body(data))
            .onErrorResume(this::handleError);
    }

    @GetMapping("/studies/{studyId}/metadata")
    public Mono<ResponseEntity<String>> getStudyMetadata(@PathVariable String studyId,
                                                        @RequestParam(name = "imageSetId", required = false) String imageSetId) {
        log.info("Fetching metadata for studyId={}, imageSetId={}", studyId, imageSetId);

        return fetchSeriesMetadata(studyId, imageSetId)
            .flatMap(seriesArray -> processSeriesMetadata(studyId, seriesArray))
            .map(this::createDicomResponse)
            .onErrorResume(this::handleError);
    }

    @GetMapping("/studies/{studyId}/series/{seriesId}/instances/{instanceId}/bulkdata/{tag}/**")
    public Mono<ResponseEntity<byte[]>> getBulkData(@PathVariable String studyId,
                                                   @PathVariable String seriesId,
                                                   @PathVariable String instanceId,
                                                   @PathVariable String tag,
                                                   HttpServletRequest request) {
        log.info("Fetching bulkdata for studyId={}, seriesId={}, instanceId={}, tag={}",
                studyId, seriesId, instanceId, tag);

        // Extract the full path including any additional path segments
        String requestPath = request.getRequestURI();
        String bulkdataPath = requestPath.substring(requestPath.indexOf("/bulkdata/"));

        return awsWebClient.get()
            .uri(baseUrl() + "/studies/" + studyId + "/series/" + seriesId + "/instances/" + instanceId + bulkdataPath)
            .retrieve()
            .bodyToMono(byte[].class)
            .map(data -> ResponseEntity.ok()
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .header("Cross-Origin-Embedder-Policy", "require-corp")
                .header("Cross-Origin-Opener-Policy", "same-origin")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data))
            .onErrorResume(e -> {
                log.error("Error fetching bulkdata: {}", e.getMessage(), e);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
            });
    }

    // Add a separate controller to handle direct GCP Healthcare API URL patterns
    @GetMapping("/v1/projects/{projectId}/locations/{locationId}/datasets/{datasetId}/dicomStores/{dicomStoreId}/dicomWeb/**")
    public Mono<ResponseEntity<byte[]>> handleGcpHealthcareApiUrls(@PathVariable String projectId,
                                                                  @PathVariable String locationId,
                                                                  @PathVariable String datasetId,
                                                                  @PathVariable String dicomStoreId,
                                                                  HttpServletRequest request) {
        log.info("Intercepting GCP Healthcare API URL: {}", request.getRequestURI());

        // Extract the dicomWeb path portion
        String requestPath = request.getRequestURI();
        String dicomWebPath = requestPath.substring(requestPath.indexOf("/dicomWeb/") + 9);

        // Forward to our AWS Medical Imaging backend
        return awsWebClient.get()
            .uri(baseUrl() + "/" + dicomWebPath)
            .retrieve()
            .bodyToMono(byte[].class)
            .map(data -> ResponseEntity.ok()
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .header("Cross-Origin-Embedder-Policy", "require-corp")
                .header("Cross-Origin-Opener-Policy", "same-origin")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data))
            .onErrorResume(e -> {
                log.error("Error handling GCP Healthcare API URL: {}", e.getMessage(), e);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
            });
    }

    private Mono<JsonNode> fetchSeriesMetadata(String studyId, String imageSetId) {
        return awsWebClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path(baseUrl() + "/studies/" + studyId + "/series");
                if (imageSetId != null && !imageSetId.isBlank()) {
                    uriBuilder.queryParam("imageSetId", imageSetId);
                }
                return uriBuilder.build();
            })
            .header(HttpHeaders.ACCEPT, "application/dicom+json")
            .retrieve()
            .bodyToMono(JsonNode.class)
            .onErrorMap(e -> new RuntimeException("Failed to fetch series metadata: " + e.getMessage(), e));
    }

    private Mono<ArrayNode> processSeriesMetadata(String studyId, JsonNode seriesArray) {
        if (!seriesArray.isArray() || seriesArray.isEmpty()) {
            return Mono.just(objectMapper.createArrayNode());
        }

        return Flux.fromIterable(seriesArray)
            .filter(JsonNode::isObject)
            .flatMap(series -> processSeriesInstances(studyId, series), 10)
            .collectList()
            .map(instances -> {
                ArrayNode enhancedArray = objectMapper.createArrayNode();
                instances.forEach(enhancedArray::add);
                return enhancedArray;
            });
    }

    private Flux<JsonNode> processSeriesInstances(String studyId, JsonNode series) {
        String seriesInstanceUid = extractUid((ObjectNode)series, "0020000E", null);
        if (seriesInstanceUid == null) {
            return Flux.empty();
        }

        return fetchInstancesMetadata(studyId, seriesInstanceUid)
            .flatMapMany(instancesArray -> {
                if (!instancesArray.isArray() || instancesArray.isEmpty()) {
                    return Flux.empty();
                }
                return processInstances(instancesArray, series);
            })
            .onErrorResume(e -> {
                log.error("Error processing series {}: {}", seriesInstanceUid, e.getMessage());
                return Flux.empty();
            });
    }

    private Mono<JsonNode> fetchInstancesMetadata(String studyId, String seriesInstanceUid) {
        return awsWebClient.get()
            .uri(baseUrl() + "/studies/" + studyId + "/series/" + seriesInstanceUid + "/instances")
            .header(HttpHeaders.ACCEPT, "application/dicom+json")
            .retrieve()
            .bodyToMono(JsonNode.class);
    }

    private Flux<JsonNode> processInstances(JsonNode instancesArray, JsonNode series) {
        return Flux.fromIterable(instancesArray)
            .map(instance -> {
                ObjectNode instanceMetadata = (ObjectNode)instance;
                copySeriesMetadata(series, instanceMetadata);
                // Transform any GCP Healthcare API URLs to use our proxy
                transformBulkDataUris(instanceMetadata);
                return instanceMetadata;
            });
    }

    private void transformBulkDataUris(ObjectNode metadata) {
        // Recursively find and transform BulkDataURI fields
        transformBulkDataUrisRecursive(metadata);
    }

    private void transformBulkDataUrisRecursive(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objNode = (ObjectNode) node;
            objNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();

                if ("BulkDataURI".equals(key) && value.isTextual()) {
                    String originalUri = value.asText();
                    if (originalUri.contains("healthcare.googleapis.com")) {
                        // Extract the path after 'dicomWeb'
                        String pattern = ".*?/dicomWeb/(.+)";
                        if (originalUri.matches(pattern)) {
                            String path = originalUri.replaceAll(pattern, "$1");
                            String newUri = "http://localhost:8080/dicomweb/" + path;
                            objNode.put(key, newUri);
                            log.debug("Transformed BulkDataURI from {} to {}", originalUri, newUri);
                        }
                    }
                } else if (value.isObject() || value.isArray()) {
                    // Recursively process nested objects and arrays
                    transformBulkDataUrisRecursive(value);
                }
            });
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                transformBulkDataUrisRecursive(item);
            }
        }
    }

    private void copySeriesMetadata(JsonNode series, ObjectNode instanceMetadata) {
        series.fields().forEachRemaining(entry -> {
            if (!instanceMetadata.has(entry.getKey())) {
                instanceMetadata.set(entry.getKey(), entry.getValue());
            }
        });
    }

    private ResponseEntity<String> createDicomResponse(ArrayNode enhancedArray) {
        try {
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/dicom+json"))
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .header("Cross-Origin-Embedder-Policy", "require-corp")
                .header("Cross-Origin-Opener-Policy", "same-origin")
                .body(objectMapper.writeValueAsString(enhancedArray));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response: " + e.getMessage(), e);
        }
    }

    private Mono<ResponseEntity<String>> handleError(Throwable error) {
        log.error("Error processing request: {}", error.getMessage(), error);
        return Mono.just(ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"error\": \"" + error.getMessage() + "\"}")
        );

                        return Mono.just(ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType("application/dicom+json"))
                                .header("Cross-Origin-Resource-Policy", "cross-origin")
                                .header("Cross-Origin-Embedder-Policy", "require-corp")
                                .header("Cross-Origin-Opener-Policy", "same-origin")
                                .body(objectMapper.writeValueAsString(enhancedArray)));

                    } catch (Exception e) {
                        log.error("Error processing metadata for study {}: {}", studyId, e.getMessage(), e);
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\": \"Error processing metadata: " + e.getMessage() + "\"}"));
                    }
                })
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5)))
                .onErrorResume(e -> {
                    log.error("Failed to fetch metadata for study {}: {}", studyId, e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\": \"Failed to fetch metadata: " + e.getMessage() + "\"}"));
                });
    }

    private String extractUid(ObjectNode metadata, String tag, String defaultValue) {
        if (metadata.has(tag) && metadata.get(tag).has("Value") && metadata.get(tag).get("Value").size() > 0) {
            return metadata.get(tag).get("Value").get(0).asText();
        }
        return defaultValue;
    }

    private Mono<String> fetchInstancesForSeries(String studyId, String seriesId) {
        return awsWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(baseUrl() + "/studies/" + studyId + "/series/" + seriesId + "/instances")
                    .build())
                .header(HttpHeaders.ACCEPT, "application/dicom+json")
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> log.debug("Retrieved instances for series {}: {}", seriesId, response));
    }

    private String extractSopInstanceUid(JsonNode instanceMetadata) {
        // AWS AHI specific SOP Instance UID extraction
        if (instanceMetadata.has("00080018")) {
            JsonNode sopNode = instanceMetadata.get("00080018");
            if (sopNode.has("Value") && sopNode.get("Value").isArray() && sopNode.get("Value").size() > 0) {
                String sopInstanceUid = sopNode.get("Value").get(0).asText();
                log.debug("Extracted SOP Instance UID: {}", sopInstanceUid);
                return sopInstanceUid;
            }
        }
        return null;
    }

    private boolean hasFrames(JsonNode instanceMetadata) {
        // Check if instance has multiple frames according to AWS AHI
        if (instanceMetadata.has("00280008")) { // Number of Frames
            JsonNode framesNode = instanceMetadata.get("00280008");
            if (framesNode.has("Value") && framesNode.get("Value").isArray() &&
                framesNode.get("Value").size() > 0) {
                int numberOfFrames = framesNode.get("Value").get(0).asInt();
                return numberOfFrames > 1;
            }
        }
        return false;
    }

    private String extractUidFromInstance(JsonNode metadata, String tag) {
        if (metadata.has(tag)) {
            JsonNode tagNode = metadata.get(tag);
            if (tagNode.has("Value") && tagNode.get("Value").isArray() &&
                tagNode.get("Value").size() > 0) {
                return tagNode.get("Value").get(0).asText();
            }
        }
        return null;
    }

    private void addDicomTag(ObjectNode metadata, String tag, String vr, String value) {
        ObjectNode tagNode = objectMapper.createObjectNode();
        tagNode.put("vr", vr);
        ArrayNode valueArray = tagNode.putArray("Value");
        valueArray.add(value);
        metadata.set(tag, tagNode);
    }

    private void addDicomTag(ObjectNode metadata, String tag, String vr, String[] values) {
        ObjectNode tagNode = objectMapper.createObjectNode();
        tagNode.put("vr", vr);
        ArrayNode valueArray = tagNode.putArray("Value");
        for (String value : values) {
            valueArray.add(value);
        }
        metadata.set(tag, tagNode);
    }

    private String baseUrl() {
        return "/medical-imaging";
    }

    private void copyInstanceTags(JsonNode source, ObjectNode target) {
        // AWS AHI specific instance tags required by OHIF viewer
        String[] instanceTags = {
            "00080018",  // SOPInstanceUID
            "00200013",  // InstanceNumber
            "00280008",  // Number of Frames
            "00280010",  // Rows
            "00280011",  // Columns
            "00280100",  // BitsAllocated
            "00280101",  // BitsStored
            "00280102",  // HighBit
            "00280103",  // PixelRepresentation
            "00280004",  // Photometric Interpretation
            "00280002",  // Samples per Pixel
            "00281050",  // Window Center
            "00281051",  // Window Width
            "00281052",  // Rescale Intercept
            "00281053"   // Rescale Slope
        };

        for (String tag : instanceTags) {
            if (source.has(tag)) {
                target.set(tag, source.get(tag).deepCopy());
                log.debug("Copied instance tag {}: {}", tag, source.get(tag));
            }
        }

        // Handle frame-specific metadata if this is a multi-frame image
        if (hasFrames(source)) {
            copyFrameMetadata(source, target);
        }
    }

    private void copyFrameMetadata(JsonNode source, ObjectNode target) {
        // AWS AHI specific frame tags required by OHIF viewer
        String[] frameTags = {
            "00280009",  // Frame Increment Pointer
            "00280008",  // Number of Frames
            "00200032",  // Image Position (Patient)
            "00200037"   // Image Orientation (Patient)
        };

        for (String tag : frameTags) {
            if (source.has(tag)) {
                target.set(tag, source.get(tag).deepCopy());
                log.debug("Copied frame tag {}: {}", tag, source.get(tag));
            }
        }
    }
}
