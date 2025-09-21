import { useState, useRef } from "react";
import Image from "next/image";
import { FaCheck } from "react-icons/fa";

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

  // Search celebrities in the graph for suggestions
  const searchCelebrities = async (q, setSuggestions, abortController) => {
    if (!q || q.trim().length < 2) { setSuggestions([]); return; }
    try {
      // Use graph-based search for exact nconst matches
      const apiUrl = (process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080').replace(/\/$/, '');
      const res = await fetch(`${apiUrl}/api/search-celebrities-graph?q=${encodeURIComponent(q)}`, {
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
          const photoRes = await fetch(`${apiUrl}/api/celebrity-photo?celebrityId=${encodeURIComponent(item.nconst)}&celebrityName=${encodeURIComponent(item.name)}`, {
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
      // Debounce to reduce server load
      searchTimeout1.current = setTimeout(() => searchCelebrities(value, setSuggestions1, abortController1.current), 250);
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
      // Debounce to reduce server load
      searchTimeout2.current = setTimeout(() => searchCelebrities(value, setSuggestions2, abortController2.current), 250);
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

  // Handle immediate search when Enter is pressed
  const handleKeyPress = async (e, field) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      const value = field === 1 ? name1.trim() : name2.trim();
      if (value) {
        const apiUrl = (process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080').replace(/\/$/, '');
        try {
          const res = await fetch(`${apiUrl}/api/search-celebrities-graph?q=${encodeURIComponent(value)}`);
          const data = await res.json();
          if (Array.isArray(data) && data.length > 0) {
            if (field === 1) {
              setSelectedId1(data[0].nconst);
              setName1(data[0].name);
              setShowSuggestions1(false);
            } else {
              setSelectedId2(data[0].nconst);
              setName2(data[0].name);
              setShowSuggestions2(false);
            }
          }
        } catch (error) {
          console.log('Search failed:', error);
        }
      }
    }
  };

  const isSelected1 = !!selectedId1;
  const isSelected2 = !!selectedId2;
  const canSubmit = isSelected1 && isSelected2;

  return (
    <form
      onSubmit={async e => {
        e.preventDefault();
        // Enforce both selections before submitting
        if (!canSubmit) return;
        onSearch(selectedId1, selectedId2);
      }}
      className="flex flex-col gap-4 items-center w-full max-w-lg"
      autoComplete="off"
    >
      <div className="relative w-full">
        <input
          ref={input1Ref}
          className={`w-full p-3 pr-10 rounded bg-gray-800 text-white placeholder-gray-400 border transition-colors duration-200 focus:outline-none ${isSelected1 ? 'border-green-500 focus:ring-2 focus:ring-green-500' : 'border-gray-700 focus:ring-2 focus:ring-blue-500'}`}
          placeholder="Enter first celebrity name..."
          value={name1}
          onChange={(e) => { handleInput1(e); onChangeName1(e); }}
          onKeyPress={(e) => handleKeyPress(e, 1)}
          onFocus={() => setShowSuggestions1(true)}
          onBlur={() => setTimeout(() => setShowSuggestions1(false), 100)}
        />
        {isSelected1 && (
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-green-400">
            <FaCheck />
          </span>
        )}
        {showSuggestions1 && suggestions1.length > 0 && (
          <ul className="absolute z-10 w-full bg-gray-900 border border-gray-700 rounded mt-1 max-h-60 overflow-y-auto">
            {suggestions1.map((item, idx) => (
                     <li
                       key={idx}
                       className="px-4 py-2 hover:bg-blue-700 cursor-pointer text-white flex items-center gap-3"
                       onMouseDown={() => handleSuggestionClick1(item)}
                     >
                       {item.photoUrl ? (
                         <Image 
                           src={item.photoUrl} 
                           alt={item.name}
                           width={32}
                           height={32}
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
          className={`w-full p-3 pr-10 rounded bg-gray-800 text-white placeholder-gray-400 border transition-colors duration-200 focus:outline-none ${isSelected2 ? 'border-green-500 focus:ring-2 focus:ring-green-500' : 'border-gray-700 focus:ring-2 focus:ring-blue-500'}`}
          placeholder="Enter second celebrity name..."
          value={name2}
          onChange={(e) => { handleInput2(e); onChangeName2(e); }}
          onKeyPress={(e) => handleKeyPress(e, 2)}
          onFocus={() => setShowSuggestions2(true)}
          onBlur={() => setTimeout(() => setShowSuggestions2(false), 100)}
        />
        {isSelected2 && (
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-green-400">
            <FaCheck />
          </span>
        )}
        {showSuggestions2 && suggestions2.length > 0 && (
          <ul className="absolute z-10 w-full bg-gray-900 border border-gray-700 rounded mt-1 max-h-60 overflow-y-auto">
            {suggestions2.map((item, idx) => (
                     <li
                       key={idx}
                       className="px-4 py-2 hover:bg-blue-700 cursor-pointer text-white flex items-center gap-3"
                       onMouseDown={() => handleSuggestionClick2(item)}
                     >
                       {item.photoUrl ? (
                         <Image 
                           src={item.photoUrl} 
                           alt={item.name}
                           width={32}
                           height={32}
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
        disabled={!canSubmit}
        aria-disabled={!canSubmit}
        className={`w-full font-bold py-3 px-6 rounded transition-colors ${canSubmit ? 'bg-blue-800 hover:bg-blue-900 text-white' : 'bg-gray-700 text-gray-300 cursor-not-allowed'}`}
      >
        Find Shortest Path
      </button>
    </form>
  );
} 