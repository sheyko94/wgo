# Project TODO

## Future domain and project name

Keep the current WGO name for now.

- [ ] Consider buying `OpenGrounded.com` if needed in the future; recheck availability
  before purchasing.
- [ ] Rename the project to match whichever domain we buy in the future.

## Incremental UI plan

1. Foundation (done): basic page, development setup, type checking, linting,
   production build, and API proxy.
2. Map (done): MapLibre GL JS, OpenFreeMap background, 2D zoom/pan, attribution,
   configurable style, and loading/error UI.
3. Events (done): `GET /v1/events`, clickable markers, loading/error/empty states,
   and manual refresh.
4. Observations: add a backend observation read endpoint, display markers, and
   toggle observation/event visibility.
5. Creation: select coordinates on the map and submit `POST /v1/observations`.
   Explain processing state; events appear after the backend processes reports
   and the user refreshes.

The backend creates or updates events through matching; the UI creates observations.

## Future AI ideas

Ideas for future iterations, not committed implementation scope. Suggested order:
improve and evaluate matching first, then add a visible event summary feature.

### 1. Real embeddings and better event matching

- [ ] Create a labeled evaluation dataset of reports that should belong to the
  same event or remain separate. Include paraphrases, nearby unrelated incidents,
  and recurring incidents at the same location on different days.
- [ ] Enable real embeddings using the existing Bedrock integration. The default
  `fixed` provider returns the same vector for every text and only tests the pipeline.
- [ ] Align model output dimensions with application and OpenSearch configuration.
  The application currently defaults to 768 dimensions; Titan Text Embeddings V2
  defaults to 1,024.
- [ ] Rebuild the OpenSearch event projection when changing embedding models or
  dimensions. Do not mix embeddings from different models.
- [ ] Measure incorrect merges and missed matches, then tune the similarity
  threshold against the labeled examples. Preserve geographic/time constraints
  and idempotent processing.

### 2. AI-generated event titles and summaries

- [ ] Start with an on-demand **Summarize event** button in event details.
- [ ] Generate a short summary from the event's linked observations and provide
  references to the observations supporting it.
- [ ] Describe reported information as reports, preserve uncertainty and conflicting
  accounts, and avoid presenting generated summaries as verification.
- [ ] Suggest a concise event title instead of simply reusing the first report.
- [ ] Evaluate factual support and usefulness on representative events before
  considering automatic generation when observations arrive.
- [ ] Decide how generated content is stored and refreshed when source observations
  change. Track model/prompt versions, latency, and cost.

### 3. Event categorization and map filters

- [x] Define categories: transport, weather, community, fire, infrastructure, and other.
- [x] Classify events with Claude Haiku 4.5 using validated structured output, with an
  unknown/other fallback for ambiguous cases.
- [x] Add category filters, marker colors, and a legend to the map.
- [ ] Evaluate category accuracy before expanding the taxonomy.

### 4. Semantic event search

- [ ] Add a search box that finds events by meaning rather than exact keywords.
  For example, “travel disruptions” could find road closures and train interruptions.
- [ ] Embed queries with the same model used for indexed events and search OpenSearch.
- [ ] Combine semantic relevance with optional geographic and time filters.
- [ ] Evaluate relevance and show useful empty/loading/error states in the UI.

### 5. Review ambiguous event matches

- [ ] Identify cases where embedding similarity does not clearly distinguish
  candidate events.
- [ ] Experiment with an LLM comparing the incoming observation against candidate
  events and their supporting observations.
- [ ] Allow an uncertain/no-match result and evaluate decisions before allowing
  this additional step to affect automatic assignments.
- [ ] Measure whether the improvement justifies added latency and cost. Keep
  assignment decisions in application code and preserve retry/idempotency behavior.

## References

- [Amazon Titan Text Embeddings models](https://docs.aws.amazon.com/bedrock/latest/userguide/titan-embedding-models.html)
