import os
import json
import google.generativeai as genai
from dotenv import load_dotenv

load_dotenv()

genai.configure(api_key=os.getenv("GEMINI_API_KEY"))

def analyze_lead(lead_data: dict):
    model = genai.GenerativeModel('gemini-1.5-flash')
    
    prompt = f"""
    Analise os seguintes dados da empresa para prospecção de anúncios no Instagram:
    {json.dumps(lead_data, indent=2)}

    Objetivo:
    1. Lead Scoring (0-100) baseado no potencial de conversão para marketing digital.
    2. Classificação: baixo, médio ou alto potencial.
    3. Diagnóstico rápido: Por que essa pontuação?
    4. Mensagem de abordagem: Uma mensagem curta e persuasiva para WhatsApp (máximo 300 caracteres).

    Retorne APENAS um JSON puro no seguinte formato:
    {{
        "score": 85,
        "potential_level": "alto",
        "marketing_diagnosis": "...",
        "generated_message": "..."
    }}
    """
    
    response = model.generate_content(prompt)
    
    # Extrair JSON da resposta (limpando markdown se necessário)
    content = response.text.strip()
    if content.startswith("```json"):
        content = content[7:-3].strip()
    elif content.startswith("```"):
        content = content[3:-3].strip()
        
    try:
        return json.loads(content)
    except:
        return {
            "score": 0,
            "potential_level": "erro",
            "marketing_diagnosis": "Falha ao processar IA",
            "generated_message": "Erro na geração."
        }
