# Product Image Classification via OpenAI Design

## Context
The goal is to implement an endpoint in the Product API that accepts an image, sends it to OpenAI for analysis, and returns a structured JSON with product details (name, brand, volume, category). The system must send existing tenant categories and brands to OpenAI to ensure the returned classification matches existing data.

## Architecture

### 1. API Endpoint
- **Path:** `POST /api/products/analyze-image`
- **Auth:** Requires `PRODUCT_CREATE` authority or `ROLE_ADMIN`.
- **Input:** `MultipartFile` (image).
- **Output:** `ProductClassificationResponse` JSON.

### 2. Service Layer (`OpenAiService`)
- Responsible for communicating with OpenAI API.
- **Responsibilities:**
  - Convert `MultipartFile` to Base64.
  - Fetch active Brands and Categories for the current tenant.
  - Construct the prompt with the allowed list of brands/categories.
  - Call OpenAI API (`gpt-5-nano`) using `RestClient` or `RestTemplate`.
  - Parse the OpenAI JSON response.
  - Map the result to internal IDs (UUIDs) for matched brands/categories.

### 3. Data Transfer Objects (DTOs)
New package: `br.com.stockshift.dto.ai`

**`ProductClassificationResponse`**
```java
public class ProductClassificationResponse {
    private String name;
    private String description; // Optional, if AI generates one

    // Brand
    private UUID brandId;
    private String brandName; // Name returned by AI

    // Category
    private UUID categoryId;
    private String categoryName; // Name returned by AI

    // Volume/Attributes
    private Double volumeValue;
    private String volumeUnit;

    private String rawCategory; // Fallback if no match found
}
```

### 4. OpenAI Integration
- **Model:** `gpt-5-nano`
- **Endpoint:** `https://api.openai.com/v1/chat/completions`
- **Payload Structure:**
  - `response_format`: `{ "type": "json_object" }` to ensure valid JSON.
  - `messages`: System prompt defining the role and the list of valid brands/categories. User message containing the image url (data:image/jpeg;base64,...).

**Prompt Strategy:**
"You are a product classifier. Analyze the image and extract product details.
Allowed Categories: [List of Category Names]
Allowed Brands: [List of Brand Names]

Return a JSON with this schema:
{
  "name": "Product Name",
  "brand": "Exact Brand Name from list or best guess",
  "volume": { "value": 100, "unit": "ml" },
  "category": "Exact Category Name from list or best guess"
}"

### 5. Security & Performance
- **Image Processing:** Image is processed in-memory (stream -> base64) and not saved to disk/storage to save costs/space.
- **Tenant Isolation:** Only brands/categories from the current authenticated tenant are sent in the prompt.
- **Error Handling:** Graceful fallback if OpenAI is down or returns invalid JSON.

## Configuration
- `application.yml` / `application-dev.yml`
  - `openai.secret-key`: Already exists.
  - `openai.model`: Defaults to `gpt-5-nano`.
  - `openai.api-url`: `https://api.openai.com/v1/chat/completions`.
