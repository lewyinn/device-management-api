import os

from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker
from sqlalchemy.pool import StaticPool

load_dotenv()

DATABASE_URL = os.getenv("DATABASE_URL")


def get_int_env(key: str, default: int) -> int:
    return int(os.getenv(key, default))


engine_options = {}
if DATABASE_URL.startswith("sqlite"):
    engine_options["connect_args"] = {"check_same_thread": False}
    engine_options["poolclass"] = StaticPool
else:
    engine_options["pool_size"] = get_int_env("DB_POOL_SIZE", 10)
    engine_options["max_overflow"] = get_int_env("DB_MAX_OVERFLOW", 5)
    engine_options["pool_timeout"] = get_int_env("DB_POOL_TIMEOUT", 30)
    engine_options["pool_recycle"] = get_int_env("DB_POOL_RECYCLE", 1800)
    engine_options["pool_pre_ping"] = True

engine = create_engine(
    DATABASE_URL,
    **engine_options,
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
