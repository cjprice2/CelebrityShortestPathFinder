import { useState, useRef } from "react";

export default function SearchForm({ onSearch }) {
  const [name1, setName1] = useState("");
  const [name2, setName2] = useState("");
  const [selectedId1, setSelectedId1] = useState("");
  const [selectedId2, setSelectedId2] = useState("");
  const [suggestions1, setSuggestions1] = useState([]);
  const [suggestions2, setSuggestions2] = useState([]);
  const [showSuggestions1, setShowSuggestions1] = useState(false);
  const [showSuggestions2, setShowSuggestions2] = useState(false);
  const input1Ref = useRef(null);
  const input2Ref = useRef(null);
  const searchTimeout1 = useRef(null);
  const searchTimeout2 = useRef(null);
  const abortController1 = useRef(null);
  const abortController2 = useRef(null);

  // Optional: could fetch photos by ID for UI embellishments later
  const fetchActorPhotoById = async () => null;

  // Search actors in the graph for suggestions
  const searchActors = async (q, setSuggestions, abortController) => {
    if (!q || q.trim().length === 0) { setSuggestions([]); return; }
    try {
      // Use graph-based search for exact nconst matches
      const res = await fetch(`/api/search-actors-graph?q=${encodeURIComponent(q)}`, {
        signal: abortController.signal
      });
      const items = await res.json();
      const suggestions = Array.isArray(items) ? items.slice(0, 10) : [];
      
      // Set suggestions immediately without photos for faster UI
      setSuggestions(suggestions);
      
      // Fetch photos in background (non-blocking) - only if we have an ID
      suggestions.forEach(async (item) => {
        if (!item.nconst) return; // Skip if no ID
        
        try {
          const photoRes = await fetch(`/api/actor-photo?actorId=${encodeURIComponent(item.nconst)}&actorName=${encodeURIComponent(item.name)}`, {
            signal: abortController.signal
          });
          const photoData = await photoRes.json();
          setSuggestions(prev => 
            prev.map(s => s.nconst === item.nconst ? { ...s, photoUrl: photoData.photoUrl } : s)
          );
        } catch {
          // Ignore photo fetch errors
        }
      });
    } catch {
      setSuggestions([]);
    }
  };

  const handleInput1 = (e) => { const v = e.target.value; setName1(v); setSelectedId1(""); };
  const onChangeName1 = (e) => {
    const value = e.target.value;
    if (searchTimeout1.current) clearTimeout(searchTimeout1.current);
    if (abortController1.current) abortController1.current.abort();
    
    if (value.length > 0) {
      abortController1.current = new AbortController();
      searchTimeout1.current = setTimeout(() => searchActors(value, setSuggestions1, abortController1.current), 300);
      setShowSuggestions1(true);
    } else {
      setSuggestions1([]);
      setShowSuggestions1(false);
    }
  };

  const handleInput2 = (e) => { const v = e.target.value; setName2(v); setSelectedId2(""); };
  const onChangeName2 = (e) => {
    const value = e.target.value;
    if (searchTimeout2.current) clearTimeout(searchTimeout2.current);
    if (abortController2.current) abortController2.current.abort();
    
    if (value.length > 0) {
      abortController2.current = new AbortController();
      searchTimeout2.current = setTimeout(() => searchActors(value, setSuggestions2, abortController2.current), 300);
      setShowSuggestions2(true);
    } else {
      setSuggestions2([]);
      setShowSuggestions2(false);
    }
  };

  const handleSuggestionClick1 = (item) => {
    setSelectedId1(item.nconst);
    setName1(item.name);
    setSuggestions1([]);
    setShowSuggestions1(false);
    if (input1Ref.current) input1Ref.current.blur();
  };

  const handleSuggestionClick2 = (item) => {
    setSelectedId2(item.nconst);
    setName2(item.name);
    setSuggestions2([]);
    setShowSuggestions2(false);
    if (input2Ref.current) input2Ref.current.blur();
  };

  // No suggestions; IDs entered directly

  return (
    <form
      onSubmit={async e => {
        e.preventDefault();
        let id1 = selectedId1;
        let id2 = selectedId2;
        
        // If no ID selected, try to find by name in graph
        if (!id1 && name1.trim()) {
          try {
            const r = await fetch(`/api/search-actors-graph?q=${encodeURIComponent(name1.trim())}`);
            const d = await r.json();
            if (Array.isArray(d) && d.length > 0) {
              id1 = d[0].nconst;
            }
          } catch {}
        }
        if (!id2 && name2.trim()) {
          try {
            const r = await fetch(`/api/search-actors-graph?q=${encodeURIComponent(name2.trim())}`);
            const d = await r.json();
            if (Array.isArray(d) && d.length > 0) {
              id2 = d[0].nconst;
            }
          } catch {}
        }
        
        if (!id1 || !id2) return;
        onSearch(id1, id2);
      }}
      className="flex flex-col gap-4 items-center w-full max-w-lg"
      autoComplete="off"
    >
      <div className="relative w-full">
        <input
          ref={input1Ref}
          className="w-full p-3 rounded bg-gray-800 text-white placeholder-gray-400 border border-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="Enter first celebrity name..."
          value={name1}
          onChange={(e) => { handleInput1(e); onChangeName1(e); }}
          onFocus={() => setShowSuggestions1(true)}
          onBlur={() => setTimeout(() => setShowSuggestions1(false), 100)}
        />
        {showSuggestions1 && suggestions1.length > 0 && (
          <ul className="absolute z-10 w-full bg-gray-900 border border-gray-700 rounded mt-1 max-h-60 overflow-y-auto">
            {suggestions1.map((item, idx) => (
                     <li
                       key={idx}
                       className="px-4 py-2 hover:bg-blue-700 cursor-pointer text-white flex items-center gap-3"
                       onMouseDown={() => handleSuggestionClick1(item)}
                     >
                       {item.photoUrl ? (
                         <img 
                           src={item.photoUrl} 
                           alt={item.name}
                           className="w-8 h-8 rounded-lg object-cover"
                           onError={(e) => {
                             e.preventDefault();
                             e.target.style.display = 'none';
                             e.target.nextSibling.style.display = 'flex';
                           }}
                         />
                       ) : null}
                       <div className={`w-8 h-8 rounded-lg bg-blue-800 flex items-center justify-center text-xs ${item.photoUrl ? 'hidden' : 'flex'}`}>
                         {item.name.charAt(0)}
                       </div>
                       <span>{item.name}</span>
                     </li>
            ))}
          </ul>
        )}
      </div>
      <div className="relative w-full">
        <input
          ref={input2Ref}
          className="w-full p-3 rounded bg-gray-800 text-white placeholder-gray-400 border border-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="Enter second celebrity name..."
          value={name2}
          onChange={(e) => { handleInput2(e); onChangeName2(e); }}
          onFocus={() => setShowSuggestions2(true)}
          onBlur={() => setTimeout(() => setShowSuggestions2(false), 100)}
        />
        {showSuggestions2 && suggestions2.length > 0 && (
          <ul className="absolute z-10 w-full bg-gray-900 border border-gray-700 rounded mt-1 max-h-60 overflow-y-auto">
            {suggestions2.map((item, idx) => (
                     <li
                       key={idx}
                       className="px-4 py-2 hover:bg-blue-700 cursor-pointer text-white flex items-center gap-3"
                       onMouseDown={() => handleSuggestionClick2(item)}
                     >
                       {item.photoUrl ? (
                         <img 
                           src={item.photoUrl} 
                           alt={item.name}
                           className="w-8 h-8 rounded-lg object-cover"
                           onError={(e) => {
                             e.preventDefault();
                             e.target.style.display = 'none';
                             e.target.nextSibling.style.display = 'flex';
                           }}
                         />
                       ) : null}
                       <div className={`w-8 h-8 rounded-lg bg-blue-800 flex items-center justify-center text-xs ${item.photoUrl ? 'hidden' : 'flex'}`}>
                         {item.name.charAt(0)}
                       </div>
                       <span>{item.name}</span>
                     </li>
            ))}
          </ul>
        )}
      </div>
      <button
        type="submit"
        className="w-full bg-blue-800 hover:bg-blue-900 text-white font-bold py-3 px-6 rounded transition-colors"
      >
        Find Shortest Path
      </button>
    </form>
  );
} 