# Open research stores

Legal only. Full text when the record is open. Metadata is not a PDF.

| Store | API | What we take |
| --- | --- | --- |
| UH ScholarSpace (DSpace 9) | `https://scholarspace.manoa.hawaii.edu/server/api` | Theses, CTAHR, UH papers the library posted. CTAHR community `622fd1d3-d29f-4dea-ac7a-609fcf5959a0`. |
| OpenAlex | `https://api.openalex.org/works` | Works graph. Filter `is_oa:true`. Free snapshot is **metadata**. Do not sync the paid PDF archive. |
| Europe PMC | `https://www.ebi.ac.uk/europepmc/webservices/rest/search` | PubMed/PMC. Query with `OPEN_ACCESS:y`. |
| arXiv | `http://export.arxiv.org/api/query` | Preprints. Label them. |
| GBIF | `https://api.gbif.org/v1/` | Species match + occurrences. Not peer review. |
| USDA PubAg / NAL | web + OpenAlex | No API key on this box. Use ScholarSpace + OpenAlex ag filter until a NAL key exists. |
| Rainfall Atlas | `http://rainfall.geography.hawaii.edu/` | Climate grids — `gardening`, not this catalog. |
| USDA PLANTS / GRIN | plants.usda.gov / npgsweb | Names/germplasm — `gardening`. |
| FoodData Central | fdc.nal.usda.gov | Nutrients — `nutrition`. |

Do not use Sci-Hub, Library Genesis, or publisher HTML that is not OA.

Polite User-Agent: `RootRecord-research-oa/1.0`. Sleep between calls.
