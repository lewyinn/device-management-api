import os

from dotenv import load_dotenv
from sqlalchemy import create_engine, text
from sqlalchemy.orm import declarative_base, sessionmaker
from sqlalchemy.pool import StaticPool

load_dotenv()

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./database.sqlite")

engine_options = {}
if DATABASE_URL.startswith("sqlite"):
    engine_options["connect_args"] = {"check_same_thread": False}
    engine_options["poolclass"] = StaticPool
else:
    engine_options["pool_size"] = int(os.getenv("DB_POOL_SIZE", "10"))
    engine_options["max_overflow"] = int(os.getenv("DB_MAX_OVERFLOW", "5"))
    engine_options["pool_timeout"] = int(os.getenv("DB_POOL_TIMEOUT", "30"))
    engine_options["pool_recycle"] = int(os.getenv("DB_POOL_RECYCLE", "1800"))
    engine_options["pool_pre_ping"] = True

engine = create_engine(DATABASE_URL, **engine_options)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def check_postgres_connection():
    with engine.connect() as connection:
        connection.execute(text("SELECT 1"))
    print("PostgreSQL connection established")


def create_postgres_tables():
    Base.metadata.create_all(bind=engine)
    print("PostgreSQL tables ready")


def close_postgres_connection():
    engine.dispose()
    print("PostgreSQL connection closed")


def get_db():
    database = SessionLocal()
    try:
        yield database
    finally:
        database.close()
