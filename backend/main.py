from fastapi import FastAPI, Depends, HTTPException
from sqlmodel import Session, select
from .database import engine, create_db_and_tables, get_session, Lead, LeadAnalysis
from .services_google import search_leads
from .services_ai import analyze_lead
from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    create_db_and_tables()
    yield

app = FastAPI(title="SDR Antigravity API", lifespan=lifespan)

@app.get("/")
async def root():
    return {"message": "SDR System with Antigravity API is running"}

@app.get("/leads", response_model=list[Lead])
async def list_leads(session: Session = Depends(get_session)):
    leads = session.exec(select(Lead)).all()
    return leads

@app.post("/leads/search")
async def search_and_store_leads(query: str, location: str = None, session: Session = Depends(get_session)):
    results = search_leads(query, location)
    db_leads = []
    for r in results:
        lead = Lead(**r)
        session.add(lead)
        db_leads.append(lead)
    session.commit()
    for lead in db_leads:
        session.refresh(lead)
    return db_leads

@app.post("/leads/{lead_id}/analyze")
async def analyze_and_store_lead(lead_id: int, session: Session = Depends(get_session)):
    lead = session.get(Lead, lead_id)
    if not lead:
        raise HTTPException(status_code=404, detail="Lead not found")
    
    # Check if already analyzed
    existing_analysis = session.exec(select(LeadAnalysis).where(LeadAnalysis.lead_id == lead_id)).first()
    if existing_analysis:
        return existing_analysis

    analysis_data = analyze_lead(lead.dict())
    analysis = LeadAnalysis(lead_id=lead_id, **analysis_data)
    session.add(analysis)
    session.commit()
    session.refresh(analysis)
    return analysis
