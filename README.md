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

```bash
docker-compose up -d
```

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

## Related Projects

- [ocr-engine](https://github.com/tettyrs-org/ocr-engine) - Python OCR extraction service
- [whatsapp-blast](https://github.com/tettyrs-org/whatsapp-blast) - WhatsApp message distribution

## Support

For issues, questions, or contributions, please open an issue in the repository.
