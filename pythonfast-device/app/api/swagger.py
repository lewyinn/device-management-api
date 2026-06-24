import os

from fastapi import FastAPI
from fastapi.openapi.utils import get_openapi

API_PREFIX = "/api/v1"
SERVER_URL = os.getenv("SERVER_URL", "http://localhost:8000/api/v1")


def configure_swagger(app: FastAPI):
    def custom_openapi():
        if app.openapi_schema:
            return app.openapi_schema

        schema = get_openapi(
            title=app.title,
            version=app.version,
            description=app.description,
            routes=app.routes,
        )
        schema["servers"] = [{"url": SERVER_URL}]

        paths = {}
        for path, methods in schema["paths"].items():
            clean_path = path.removeprefix(API_PREFIX)
            paths[clean_path or path] = methods
        schema["paths"] = paths

        for path in schema["paths"].values():
            for operation in path.values():
                operation["responses"].pop("422", None)

        app.openapi_schema = schema
        return schema

    app.openapi = custom_openapi
