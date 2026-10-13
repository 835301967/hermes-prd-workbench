"""Tool schemas seen by Hermes."""

LIST_SOURCES = {
    "name": "list_sources",
    "description": "List materials attached to the current workbench run. Always call this first.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {
                "type": "string",
                "description": "Workbench runId from the task prompt (workbenchRunId=...)",
            }
        },
        "required": ["runId"],
    },
}

READ_SOURCE = {
    "name": "read_source",
    "description": "Read one material page. Use nextCursor until it is null.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string", "description": "Workbench runId"},
            "sourceId": {"type": "string", "description": "Source id from list_sources"},
            "cursor": {"type": "string", "description": "Opaque cursor from the previous page"},
        },
        "required": ["runId", "sourceId"],
    },
}

SUBMIT_RESULT = {
    "name": "submit_result",
    "description": "Submit the final JSON payload for the current mode. This is the only formal result.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string", "description": "Workbench runId"},
            "mode": {
                "type": "string",
                "enum": ["REVIEW", "GENERATE", "DEMO", "POC", "ESTIMATE", "TEST_CASE", "TECH_SPEC"],
            },
            "payload": {
                "type": "object",
                "description": "Result object matching the selected mode schema. REVIEW list entries in blockingIssues/otherSuggestions/readyItems are objects with a nonempty string title, never plain strings. DEMO uses workspaceFile=index.html; POC uses workspaceFile=index.html plus a files list, never file bodies.",
            },
        },
        "required": ["runId", "mode", "payload"],
    },
}

WRITE_DEMO_FILE = {
    "name": "write_demo_file",
    "description": "Write a file directly into the current run's isolated sandbox workspace (index.html only).",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string", "description": "Workbench runId"},
            "filePath": {"type": "string", "description": "Relative file path inside the run workspace (index.html)"},
            "content": {
                "type": "string",
                "description": "Complete file text. index.html must include </html> in the same call; never send a truncated page.",
            },
        },
        "required": ["runId", "filePath", "content"],
    },
}

READ_DEMO_FILE = {
    "name": "read_demo_file",
    "description": "Read a file from the current run's isolated sandbox workspace.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string", "description": "Workbench runId"},
            "filePath": {"type": "string", "description": "Relative file path inside the run workspace"},
        },
        "required": ["runId", "filePath"],
    },
}

VERIFY_DEMO = {
    "name": "verify_demo",
    "description": "Run limited automated diagnostics on generated Demo HTML for syntax, placeholders, declared action wiring,; generated scripts are never executed. Passing does not prove every business action, state transition, or browser behavior. The initial failed diagnostic permits at most two targeted repairs.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string", "description": "Workbench runId"},
            "filePath": {"type": "string", "description": "Optional workspace relative file path (e.g. index.html)"},
            "html": {"type": "string", "description": "Optional raw Demo HTML text string"},
        },
        "required": ["runId"],
    },
}

WRITE_POC_FILE = {
    "name": "write_poc_file",
    "description": "Write one complete static POC file into the current run workspace. Allowed: index.html, css/*.css, js/**/*.js, data/*.json. Do not overwrite data/required-modules.json. Call once per file; js/html/css content should stay under 20000 characters so the model turn is not truncated.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string", "description": "Workbench runId"},
            "filePath": {"type": "string", "description": "Relative POC path"},
            "content": {
                "type": "string",
                "description": "Complete file text for this path only. Never send truncated JS/JSON/HTML. js/html/css ≤ 20000 chars; JSON ≤ 28000 chars. If the tool returns truncated=true, rewrite a smaller complete file instead of retrying the same blob.",
            },
        },
        "required": ["runId", "filePath", "content"],
    },
}

READ_POC_FILE = {
    "name": "read_poc_file",
    "description": "Read a POC workspace file.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
            "filePath": {"type": "string"},
        },
        "required": ["runId", "filePath"],
    },
}

LIST_POC_FILES = {
    "name": "list_poc_files",
    "description": "List files already written to the POC workspace.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
        },
        "required": ["runId"],
    },
}

VERIFY_POC = {
    "name": "verify_poc",
    "description": "Diagnose the POC workspace: required files including data/catalog.json, catalog/menu/view/mock alignment, JS syntax, hash router, placeholders, banned fetch/Vue/HTTP, lazy appendChild stacking, and runtime view-switch renders. Passing is not a full UX proof. At most two targeted repairs.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
        },
        "required": ["runId"],
    },
}

WRITE_PRD_FILE = {
    "name": "write_prd_file",
    "description": "Write one complete PRD pack file into the GENERATE sandbox. Allowed: README.md, root *.md, modules/*.md, data/*.json. Markdown ≤ 18000 chars, JSON ≤ 28000. If truncated=true, rewrite a smaller complete file; do not retry the same content or drop catalog modules.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
            "filePath": {"type": "string"},
            "content": {"type": "string"},
        },
        "required": ["runId", "filePath", "content"],
    },
}

READ_PRD_FILE = {
    "name": "read_prd_file",
    "description": "Read one GENERATE sandbox file by relative path.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
            "filePath": {"type": "string"},
        },
        "required": ["runId", "filePath"],
    },
}

LIST_PRD_FILES = {
    "name": "list_prd_files",
    "description": "List GENERATE sandbox files (README.md, *.md, modules/*.md, data/*.json).",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
        },
        "required": ["runId"],
    },
}

VERIFY_PRD = {
    "name": "verify_prd",
    "description": "Diagnose the GENERATE pack: catalog alignment, required chapters, field matrix, mermaid fences, AC-/BR-, permission matrix, placeholders, reserved empty chapters. Structural only, not business judgment. At most two targeted repairs.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
        },
        "required": ["runId"],
    },
}

WRITE_TEST_CASE_FILE = {
    "name": "write_test_case_file",
    "description": "Write one TEST_CASE pack file. Allowed: README.md, modules/*.json, data/*.json. Do not overwrite data/required-modules.json. JSON ≤ 28000 chars.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
            "filePath": {"type": "string"},
            "content": {"type": "string"},
        },
        "required": ["runId", "filePath", "content"],
    },
}

READ_TEST_CASE_FILE = {
    "name": "read_test_case_file",
    "description": "Read one TEST_CASE sandbox file.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
            "filePath": {"type": "string"},
        },
        "required": ["runId", "filePath"],
    },
}

LIST_TEST_CASE_FILES = {
    "name": "list_test_case_files",
    "description": "List TEST_CASE sandbox files.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
        },
        "required": ["runId"],
    },
}

VERIFY_TEST_CASE = {
    "name": "verify_test_case",
    "description": "Diagnose TEST_CASE pack: catalog keys, modules/<key>.json items with title/expectedResult, required-modules coverage. At most two targeted repairs.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
        },
        "required": ["runId"],
    },
}

WRITE_TECH_SPEC_FILE = {
    "name": "write_tech_spec_file",
    "description": "Write one TECH_SPEC pack file. Allowed: README.md, architecture.md, *.md, modules/*, data/*.json. Do not overwrite data/required-modules.json.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
            "filePath": {"type": "string"},
            "content": {"type": "string"},
        },
        "required": ["runId", "filePath", "content"],
    },
}

READ_TECH_SPEC_FILE = {
    "name": "read_tech_spec_file",
    "description": "Read one TECH_SPEC sandbox file.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
            "filePath": {"type": "string"},
        },
        "required": ["runId", "filePath"],
    },
}

LIST_TECH_SPEC_FILES = {
    "name": "list_tech_spec_files",
    "description": "List TECH_SPEC sandbox files.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
        },
        "required": ["runId"],
    },
}

VERIFY_TECH_SPEC = {
    "name": "verify_tech_spec",
    "description": "Diagnose TECH_SPEC pack: architecture.md, data/tasks.json, optional tables/apis/catalog and required-modules coverage. At most two targeted repairs.",
    "parameters": {
        "type": "object",
        "properties": {
            "runId": {"type": "string"},
        },
        "required": ["runId"],
    },
}
