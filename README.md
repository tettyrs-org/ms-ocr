# MS-OCR: Multi-Source OCR Correction Engine

A Java-based correction engine for OCR output validation, normalization, and enrichment. Transforms raw OCR results into structured, validated field data with support for regional context and business rules.

## Features

### Field Type Corrections
- **Date Types**: Multi-format date parsing with Indonesian locale support
- **Nominal Types**: Currency and amount validation with Indonesian locale
- **Name Types**: Name field normalization and validation
- **Region Types**: Geographic location mapping and alias resolution
- **Rank/Grade Types**: Military/civil rank and grade classification
- **Duration Types**: Time duration and day count calculations
- **Quantity Types**: Item counting and quantity normalization

### Smart Processing
- **Label Matcher**: Intelligent OCR label-to-field mapping
- **Lexicon Matching**: Fuzzy matching against predefined dictionaries
- **Alias Resolution**: Regional and administrative boundary mapping
- **Integration Tests**: Real-world Surat Tugas (Official Assignment Letter) validation

## Technology Stack

- **Runtime**: Java 11+
- **Build**: Maven 3.8+
- **Testing**: JUnit 5, AssertJ
- **Containerization**: Docker & Docker Compose
- **Configuration**: Regional data (regencies.csv, region mappings)

## Project Structure

```
src/
├── main/java/org/tettyrs/msocr/correction/
│   ├── fieldtype/           # Field type validators and processors
│   │   ├── NameType.java
│   │   ├── RegionType.java
│   │   ├── JumlahRincian.java
│   │   └── ... (other types)
│   └── LabelMatcher.java    # OCR label matching logic
├── test/java/...            # Comprehensive unit and integration tests
└── resources/
    └── correction/          # Data files and mappings
        ├── regions.txt
        ├── region_aliases.txt
        └── kecamatan_mapping.txt
```

## Getting Started

### Prerequisites
- Java 11 or higher
- Maven 3.8 or higher
- Docker & Docker Compose (optional, for containerized setup)

### Build

```bash
./mvnw clean package
```

### Run Tests

```bash
./mvnw test
```

### Docker Setup

Run as part of the full OCR stack from project root:

```bash
cd /path/to/Projects/OCR

# Start complete microservices stack
docker-compose --env-file .env up -d

# Or run standalone with Docker
docker build -t ms-ocr:latest ./ms-ocr
docker run -p 8080:8080 \
  -e OCR_ENGINE_URL=http://ocr-engine:8000 \
  -e LOG_LEVEL=INFO \
  ms-ocr:latest
```

### Service Integration

When using docker-compose, the service is automatically available:

```bash
# From other containers (e.g., ocr-api)
curl http://ms-ocr:8080/q/health/live

# From host machine
curl http://localhost:8080/q/health/live
```

### Environment Variables

| Variable | Purpose | Default |
|----------|---------|---------|
| `QUARKUS_HTTP_HOST` | Bind address | 0.0.0.0 |
| `QUARKUS_HTTP_PORT` | Bind port | 8080 |
| `OCR_ENGINE_URL` | OCR Engine endpoint | http://ocr-engine:8000 |
| `LOG_LEVEL` | Logging level | INFO |
| `CORRECTOR_VERSION` | Service version | 1.0 |

All variables are configured in root `.env` file for docker-compose deployments.

## Usage Example

```java
// Create a name type correction
NameType nameCorrection = new NameType();
String correctedName = nameCorrection.correct(ocrExtractedName);

// Region mapping and validation
RegionType regionCorrection = new RegionType();
String province = regionCorrection.resolveRegion(ocrRegionText);

// Quantity/count field correction
JumlahRincian itemCounter = new JumlahRincian();
int count = itemCounter.parseCount(ocrCountText);
```

## Configuration

Regional data and mappings are configured through CSV files:
- `regencies.csv`: Indonesian regency/district mappings
- `region_aliases.txt`: Common aliases for regional names
- `kecamatan_mapping.txt`: Sub-district mappings

## Testing

The project includes:
- **Unit Tests**: Individual field type validations
- **Integration Tests**: Real Surat Tugas document processing (`SuratTugasIntegrationTest`)
- **Label Matcher Tests**: OCR label-to-field mapping validation

Run tests:
```bash
./mvnw test
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit changes (`git commit -m 'feat: add your feature'`)
4. Push to branch (`git push origin feature/your-feature`)
5. Create a Pull Request

## License

Part of the Tettyrs OCR ecosystem.

## Known limitations

Listed openly so they are not mistaken for solved problems:

- **Failed jobs are retried up to 5 times** (`MAX_ATTEMPTS`), then moved to a dead-letter stream. Permanent engine errors are acknowledged without retry.
- **Rules depend on the engine's `text` shape.** Extraction reads the `text` field line by line. A change in how `ocr-engine` joins words or lines can change extraction results. Use `words` and `bbox` where layout matters.
- **New engine fields are ignored until mapped.** Quarkus ignores unknown JSON properties by default and no override is configured, so `bbox_original` from `ocr-engine` is not used yet.
- **NIK is used only when it is strong.** The engine accepts a NIK when at least 5 votes agree and they make up at least 60%. Otherwise the NIK is left empty, and vision may fill it.

## Tracing

OpenTelemetry tracing is off by default. Enable it with:

- `OCR_TRACES_EXPORTER=otlp` (default `none`)
- `OCR_TRACES_ENDPOINT` (OTLP/HTTP endpoint, default `http://jaeger:4318/v1/traces`)
- `OCR_SERVICE_NAME` (service name on traces)

The trace context travels with each job in the Redis stream as a `traceparent` field. The consumer continues the trace in a child span named `process job`. Span attributes contain only the message id, never document content.


## Related Projects

- [ocr-engine](https://github.com/tettyrs-org/ocr-engine) - Python OCR extraction service that this service calls
- [ocr-api](https://github.com/tettyrs-org/ocr-api) - Public API and document lifecycle
- [ocr-app](https://github.com/tettyrs-org/ocr-app) - Flutter web app and Backend-for-Frontend (BFF)

## Support

For issues, questions, or contributions, please open an issue in the repository.
