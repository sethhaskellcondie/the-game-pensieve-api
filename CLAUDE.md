# The Game Pensieve API — Claude Instructions

## After Making Code Changes

1. **Run the linter** after finishing any code changes:
   ```
   ./mvnw checkstyle:check
   ```
   Fix any violations before considering the task complete.

2. **Update `openapi.yaml`** if the change affects any of the following:
   - Request or response shapes (new fields, removed fields, type changes)
   - New or removed endpoints
   - Validation rules or error behavior
   - Filter/sort/pagination behavior

3. **Update `api.postman_collection.json`** if the change affects example requests — add or update request bodies to reflect the new capability.
