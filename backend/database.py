from typing import Optional
from sqlmodel import Field, SQLModel, create_engine, Session
from datetime import datetime

class Lead(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    name: str
    city: str
    phone: Optional[str] = None
    website: Optional[str] = None
    rating: Optional[float] = None
    reviews_count: Optional[int] = None
    category: Optional[str] = None
    instagram_handle: Optional[str] = None
    created_at: datetime = Field(default_factory=datetime.now)

class LeadAnalysis(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    lead_id: int = Field(foreign_key="lead.id")
    score: int
    potential_level: str  # baixo, médio, alto
    marketing_diagnosis: str
    generated_message: str
    created_at: datetime = Field(default_factory=datetime.now)

sqlite_file_name = "sdr_system.db"
sqlite_url = f"sqlite:///../{sqlite_file_name}"

engine = create_engine(sqlite_url, echo=True)

def create_db_and_tables():
    SQLModel.metadata.create_all(engine)

def get_session():
    with Session(engine) as session:
        yield session
