import os
import requests
import json
from flask import Flask, request, jsonify
from dotenv import load_dotenv
from ibm_cloud_sdk_core.authenticators import IAMAuthenticator

# --- Load Environment Variables ---
load_dotenv()

# --- Initialize Flask App ---
app = Flask(__name__)

# --- Load API Key & Project ID from .env file ---
API_KEY = os.getenv("IBM_API_KEY")
PROJECT_ID = os.getenv("IBM_PROJECT_ID")

if not API_KEY or not PROJECT_ID:
    raise ValueError("IBM_API_KEY and IBM_PROJECT_ID must be set in your .env file")

# --- New function to only get the access token ---
def get_access_token():
    """Authenticates with IBM Cloud and returns a temporary access token."""
    try:
        authenticator = IAMAuthenticator(API_KEY)
        access_token = authenticator.token_manager.get_token()
        print("✅ Successfully generated a new access token.")
        return access_token, None
    except Exception as e:
        print(f"❌ Failed to generate token: {e}")
        return None, e

# --- Main API Route for Chaining the Agents ---
@app.route("/api/analyze_journey", methods=['POST'])
def analyze_journey():
    client_data = request.get_json()
    if not client_data or 'start_location' not in client_data or 'end_location' not in client_data:
        return jsonify({"error": "Missing start_location or end_location"}), 400

    start_loc = client_data['start_location']
    end_loc = client_data['end_location']

    # 1. Get a fresh token for this session
    access_token, error = get_access_token()
    if error:
        return jsonify({"error": f"Failed to authenticate with IBM: {error}"}), 500
    
    # --- STAGE 1: Call the Route Finding Agent ---
    print("Calling Agent 1: Route Finder...")
    url = "https://us-south.ml.cloud.ibm.com/ml/v1/text/generation?version=2023-05-29"
    body1 = {
	"input": f"""You are a Route Finding Assistant. Your job is to take a start (Point A) and end (Point B) location and provide three distinct, viable multi modal routes. 

    Input: 
    "start_location": "Mountain View, CA, USA",  
    "end_location": "Ferry Building, San Francisco, CA, USA"  
    
    Output:  Mountain View, CA to the Ferry Building in San Francisco, CA.  
    1. Caltrain Commuter  
    Time: 85 mins | Cost: $9.75  
    Plan: Walk (10 min) → Caltrain from Mountain View to SF (60 min) → Muni Bus to Embarcadero (15 min).  
    2. Express Bus & Subway  
    Time: 100 mins | Cost: $20.50  
    Plan: Ride-share (10 min) → SamTrans Bus to Daly City (75 min) → BART train to Embarcadero (15 min).  
    3. Scenic Drive & Ferry  
    Time: 92 mins | Cost: $28.50  
    Plan: Drive to Jack London Square in Oakland (60 min) → Ferry to SF Ferry Building (30 min) → Arrive.
    ---END---

    Input: "start_location": "Cubbon Park, Bangalore", "end_location": "Lalbagh Botanical Garden, Bangalore", 
    Output: Cubbon Park, Bangalore to Lalbagh Botanical Garden, Bangalore. 1. Auto-rickshaw & Walk Time: 20 mins | Cost: ₹50 Plan: Auto-rickshaw (10 min) → Walk (10 min) through Cubbon Park to Lalbagh. 2. Bus & Walk Time: 35 mins | Cost: ₹15 Plan: KSRTC Bus 200 from Cubbon Park to Lalbagh (25 min) → Walk (10 min) from bus stop to Lalbagh. 3. Cycle & Walk Time: 25 mins | Cost: Free Plan: Cycle (15 min) from Cubbon Park to Lalbagh Bot
    ---END---

    Input: "start_location": "Kochi Infopark", "end_location": "North Railway Station", 

    Output:  Kochi Infopark to North Railway Station, Kochi.

    1. Auto-rickshaw & Walk Time: 25 mins | Cost: ₹100 Plan: Auto-rickshaw (10 min) → Walk (15 min) to North Railway Station.
    2. Bus & Walk Time: 40 mins | Cost: ₹25 Plan: City Bus 10A (30 min) → Walk (10 min) to North Railway Station.
    3. Metro & Walk Time: 35 mins | Cost: ₹25 Plan: Kochi Metro to Edappally (25 min) → Walk (10 min) to North Railway Station.
    ---END---

    Input: 
    "start_location": "New York City, NY",
    "end_location": "Chicago, IL"
    
    Output: New York City, NY to Chicago, IL.
    1. Direct Flight
    Time: 4.5 hours | Cost: $250 | CO₂ Footprint: High
    Plan: Taxi to JFK Airport (45 min) → Flight from JFK to ORD (2.5 hours) → Taxi from ORD to downtown (45 min).
    2. Amtrak Train
    Time: 20 hours | Cost: $150 | CO₂ Footprint: Low
    Plan: Direct Amtrak train ride from Penn Station, NYC to Union Station, Chicago.
    3. Greyhound Bus
    Time: 22 hours | Cost: $90 | CO₂ Footprint: Medium
    Plan: Direct Greyhound bus ride from Port Authority, NYC to the Chicago bus terminal.
    ---END---

    Input:  "start_location": "{start_loc}", "end_location": "{end_loc}", 
    Output:""",
        "parameters": {
            "decoding_method": "greedy",
            "max_new_tokens": 500,
            "min_new_tokens": 0,
            "stop_sequences": ["---END---"],
            "repetition_penalty": 1.28
        },
        "model_id": "ibm/granite-3-3-8b-instruct",
        "project_id": "ab9a37cf-95af-47c7-8f0d-cafde4c0a3de",
        "moderations": {
            "hap": {
                "input": {
                    "enabled": True,
                    "threshold": 0.5,
                    "mask": {
                        "remove_entity_value": True
                    }
                },
                "output": {
                    "enabled": True,
                    "threshold": 0.5,
                    "mask": {
                        "remove_entity_value": True
                    }
                }
            },
            "pii": {
                "input": {
                    "enabled": True,
                    "threshold": 0.5,
                    "mask": {
                        "remove_entity_value": True
                    }
                },
                "output": {
                    "enabled": True,
                    "threshold": 0.5,
                    "mask": {
                        "remove_entity_value": True
                    }
                }
            },
            "granite_guardian": {
                "input": {
                    "threshold": 1
                }
            }
        }
    }

    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "Authorization": f"Bearer {access_token}"
    }
    # Make the call for Agent 1
    response1 = requests.post(url, headers=headers, json=body1)
    if response1.status_code != 200:
        return jsonify({"error": "Agent 1 (Route Finder) failed", "details": response1.json()}), 502


    processed_routes = response1.json()['results'][0]['generated_text'].split("---END---")[0].strip()
    print("Agent 1 Success. Output:\n", processed_routes)

    # --- STAGE 2: Call the Travel Analyst Agent ---
    print("\nCalling Agent 2: Travel Analyst...")


    # Build the request for Agent 2
    body2 = {   
	"input": f"""You are an expert travel analyst specializing in sustainable and efficient multimodal transportation. Your task is to analyze the provided travel routes between an origin and a destination.

    The input will describe the journey, followed by several numbered routes. Each route will include a title, summary stats for time, cost, and CO₂ footprint, and a step-by-step plan.

    You must evaluate each route to find the fastest, most cost-effective, and most eco-friendly option. Crucially, you must factor in the provided current weather conditions into your analysis, as they can impact travel times, safety, and comfort.

    Provide a concise summary of your analysis. Then, for each category (Fastest, Most Cost-Effective, Most Eco-Friendly), clearly state which route is the winner and provide a brief justification for your choice, referencing the weather where relevant.

    Input: Jayanagar 4th Block, Bengaluru to MG Road Metro Station, Bengaluru.

    1. Metro & Walk
    Time: 35 mins | Cost: ₹40
    Plan: Walk (10 min) → Namma Metro from Jayanagar to MG Road (20 min) → Walk (5 min).

    2. Direct BMTC Bus
    Time: 50 mins | Cost: ₹25 
    Plan: Walk (5 min) → BMTC Bus (Vajra - AC) from Jayanagar Bus Stand to Mayo Hall (40 min) → Walk (5 min).

    3. Auto-rickshaw Ride
    Time: 25 mins | Cost: ₹180
    Plan: Direct auto-rickshaw ride from origin to destination (25 min).
    Output: Current Weather: Overcast, chance of light drizzle, 26°C.
    Fastest Route: 3. Auto-rickshaw Ride

    Output: Current Weather: Overcast, chance of light drizzle, 26°C.
    Fastest Route: 3. Auto-rickshaw Ride| CO₂ Footprint: High

        Justification: This route is a direct, door-to-door service with the shortest travel time of 25 minutes, avoiding any transfers.

    Most Cost-Effective Route: 2. Direct BMTC Bus | CO₂ Footprint: Medium

        Justification: At only ₹25, the bus is the most budget-friendly option by a significant margin.

    Most Eco-Friendly Route: 1. Metro & Walk | CO₂ Footprint: Low

        Justification: The electric Namma Metro has the lowest emissions per passenger. It also offers a reliable schedule unaffected by road traffic and protection from the potential drizzle, making it the most practical and sustainable choice in this weather.
        ---END---


    Input: Mountain View, CA to the Ferry Building in San Francisco, CA.  
        1. Caltrain Commuter  
        Time: 85 mins | Cost: $9.75  
        Plan: Walk (10 min) → Caltrain from Mountain View to SF (60 min) → Muni Bus to Embarcadero (15 min).  
        2. Express Bus & Subway  
        Time: 100 mins | Cost: $20.50  
        Plan: Ride-share (10 min) → SamTrans Bus to Daly City (75 min) → BART train to Embarcadero (15 min).  
        3. Scenic Drive & Ferry  
        Time: 92 mins | Cost: $28.50  
        Plan: Drive to Jack London Square in Oakland (60 min) → Ferry to SF Ferry Building (30 min) → Arrive.
    ---END---
    Output:  Current Weather: Clear skies, 22°C.

    Fastest Route: 1. Caltrain Commuter | CO₂ Footprint: Medium

        Justification: Despite the scenic drive option being faster than the bus, the Caltrain is still the fastest due to its direct rail link, avoiding traffic congestion.

    Most Cost-Effective Route: 1. Caltrain Commuter | CO₂ Footprint: Medium

        Justification: The Caltrain offers the most affordable option at $9.75, making it the most economical choice.

    Most Eco-Friendly Route: 3. Scenic Drive & Ferry | CO₂ Footprint: High

        Justification: Although the ferry has a higher CO₂ footprint per passenger than the train, the scenic drive through the beautiful coastal areas and the ferry ride across the bay present a unique, low-emission experience compared to the bus and driving alone. The clear weather enhances the scenic value of this route.

    ---END---


    Input: {processed_routes}
    Output:""",
        "parameters": {
            "decoding_method": "greedy",
            "max_new_tokens": 500,
            "min_new_tokens": 0,
            "stop_sequences": ["---END---"],
            "repetition_penalty": 1.28
        },
        "model_id": "ibm/granite-3-3-8b-instruct",
        "project_id": "ab9a37cf-95af-47c7-8f0d-cafde4c0a3de",
        "moderations": {
            "hap": {
                "input": {
                    "enabled": True,
                    "threshold": 0.5,
                    "mask": {
                        "remove_entity_value": True
                    }
                },
                "output": {
                    "enabled": True,
                    "threshold": 0.5,
                    "mask": {
                        "remove_entity_value": True
                    }
                }
            },
            "pii": {
                "input": {
                    "enabled": True,
                    "threshold": 0.5,
                    "mask": {
                        "remove_entity_value": True
                    }
                },
                "output": {
                    "enabled": True,
                    "threshold": 0.5,
                    "mask": {
                        "remove_entity_value": True
                    }
                }
            },
            "granite_guardian": {
                "input": {
                    "threshold": 1
                }
            }
        }
    }
    
    # Make the call for Agent 2
    response2 = requests.post(url, headers=headers, json=body2)
    if response2.status_code != 200:
        return jsonify({"error": "Agent 2 (Travel Analyst) failed", "details": response2.json()}), 502

    final_analysis_text = response2.json()['results'][0]['generated_text']
    final_analysis_text = final_analysis_text.split("---END---")[0].strip()
    print("Agent 2 Success. Final Analysis:\n", final_analysis_text)
    
    return jsonify({
        "routes": processed_routes,
        "analysis": final_analysis_text
    })

if __name__ == '__main__':
    app.run(debug=True, port=5000)