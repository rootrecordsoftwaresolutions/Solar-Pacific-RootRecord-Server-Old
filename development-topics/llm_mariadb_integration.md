# LLMs and MariaDB Integration

Large Language Models (LLMs) can read and interact with data stored inside a **MariaDB** database when it is exposed through an integration framework, tool-use wrapper, or agentic standard.

## Core Access Mechanisms

### 1. Model Context Protocol (MCP)
The **Model Context Protocol (MCP)** provides an open standard for connecting AI models to data sources. An MCP server designed for MariaDB allows AI agents to:
* Inspect database schemas safely.
* Execute structured `SELECT` queries to retrieve records.
* Generate context-aware answers based on real-time database state.

### 2. Retrieval-Augmented Generation (RAG) & Vector Search
MariaDB supports semantic search through vector extensions. 
* Text embedding data can be stored directly inside the database.
* LLMs can leverage functions like `VEC_DISTANCE()` to run similarity searches.
* Relevant matching rows are pulled and fed into the LLM prompt as context.

### 3. Middleware & AI Orchestration
Frameworks like **LlamaIndex**, **LangChain**, or **MindsDB** act as a bridge between application databases and LLM prompting workflows. They dynamically translate natural language questions into valid SQL queries, fetch the data, and return a synthesized response.

## Security Considerations
When allowing an LLM to read a MariaDB database, it is best practice to:
* Enforce **Read-Only Permissions** for the database user account used by the AI.
* Implement strict input sanitization to prevent prompt injection or accidental data leaks.
* Limit schema exposure to only the specific tables relevant to the application's scope.