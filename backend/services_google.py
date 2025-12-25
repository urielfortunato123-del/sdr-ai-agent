import os
import requests
from dotenv import load_dotenv

load_dotenv()

API_KEY = os.getenv("GOOGLE_PLACES_API_KEY")

def search_leads(query: str, location: str = None):
    """
    Pesquisa empresas no Google Places e normaliza o resultado.
    """
    url = "https://maps.googleapis.com/maps/api/place/textsearch/json"
    params = {
        "query": f"{query} em {location}" if location else query,
        "key": API_KEY,
        "language": "pt-BR"
    }
    
    response = requests.get(url, params=params)
    results = response.json().get("results", [])
    
    leads = []
    for place in results:
        leads.append({
            "name": place.get("name"),
            "city": location or "Desconhecida",
            "rating": place.get("rating"),
            "reviews_count": place.get("user_ratings_total"),
            "category": place.get("types")[0] if place.get("types") else "N/A",
            "website": None, # Exige busca por details, omitido no MVP inicial
            "phone": None,   # Exige busca por details, omitido no MVP inicial
        })
    return leads
