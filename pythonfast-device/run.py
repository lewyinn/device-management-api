import os
from dotenv import load_dotenv

import uvicorn

if __name__ == "__main__":
    load_dotenv()
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=int(os.getenv("PORT", "8000")),
        reload=True,
        log_level="info"
    )
