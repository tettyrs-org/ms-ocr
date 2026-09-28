#!/usr/bin/env python3
"""
Generate region_aliases.txt from Kemendagri regencies.csv.
"""

import csv
import sys

def normalize_name(name):
    """Normalize name for alias generation."""
    return name.strip().upper()

def generate_aliases(csv_path):
    """Generate alias entries from CSV."""
    aliases = set()
    
    with open(csv_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f, delimiter=';')
        for row in reader:
            name = row.get('name', '').strip().strip('"')
            if not name:
                continue
            
            name_upper = normalize_name(name)
            
            # Generate common aliases
            alias_set = set()
            
            # 1. "KOTA X" and "KAB. X" variants
            if name_upper.startswith('KOTA '):
                base = name_upper[5:]
                alias_set.add(f"KOTA {base}")
                alias_set.add(f"KOTA {base} KOTA")
            elif name_upper.startswith('KAB. '):
                base = name_upper[5:]
                alias_set.add(f"KAB. {base}")
                alias_set.add(f"KABUPATEN {base}")
            elif name_upper.startswith('KABUPATEN '):
                base = name_upper[10:]
                alias_set.add(f"KAB. {base}")
                alias_set.add(f"KABUPATEN {base}")
            else:
                # Plain name - add Kota/Kab variants
                alias_set.add(f"KOTA {name_upper}")
                alias_set.add(f"KAB. {name_upper}")
                alias_set.add(f"KABUPATEN {name_upper}")
            
            # 2. Remove "KOTA"/"KABUPATEN"/"KAB." prefix if present
            if name_upper.startswith('KOTA '):
                alias_set.add(name_upper[5:])
            if name_upper.startswith('KABUPATEN '):
                alias_set.add(name_upper[10:])
            if name_upper.startswith('KAB. '):
                alias_set.add(name_upper[5:])
            
            # 3. Add each alias -> official name mapping
            for alias in alias_set:
                if alias != name_upper:  # Don't add self-referential
                    aliases.add(f"{alias} -> {name_upper}")
    
    return sorted(aliases)

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 generate_aliases.py <regencies.csv>", file=sys.stderr)
        sys.exit(1)
    
    csv_path = sys.argv[1]
    
    # Header
    print("# Alias untuk nama daerah")
    print("# Format: alias -> nama resmi")
    print("# Alias digunakan untuk pengenalan, bukan penggantian nilai")
    print()
    print("# Provinsi (singkatan & nama lama)")
    print("NANGGROE ACEH DARUSSALAM -> ACEH")
    print("YOGYAKARTA -> DI YOGYAKARTA")
    print("JOGJA -> DI YOGYAKARTA")
    print("JOGJAKARTA -> DI YOGYAKARTA")
    print("YOGYA -> DI YOGYAKARTA")
    print("JAKARTA -> DKI JAKARTA")
    print("JAKARTA RAYA -> DKI JAKARTA")
    print("JABAR -> JAWA BARAT")
    print("JATENG -> JAWA TENGAH")
    print("JATIM -> JAWA TIMUR")
    print("KALBAR -> KALIMANTAN BARAT")
    print("KALSEL -> KALIMANTAN SELATAN")
    print("KALTENG -> KALIMANTAN TENGAH")
    print("KALTIM -> KALIMANTAN TIMUR")
    print("KALTARA -> KALIMANTAN UTARA")
    print("BANGKA BELITUNG -> KEPULAUAN BANGKA BELITUNG")
    print("BABEL -> KEPULAUAN BANGKA BELITUNG")
    print("KEPRI -> KEPULAUAN RIAU")
    print("MALUT -> MALUKU UTARA")
    print("NTB -> NUSA TENGGARA BARAT")
    print("NTT -> NUSA TENGGARA TIMUR")
    print("SULBAR -> SULAWESI BARAT")
    print("SULSEL -> SULAWESI SELATAN")
    print("SULTENG -> SULAWESI TENGAH")
    print("SULTRA -> SULAWESI TENGGARA")
    print("SULUT -> SULAWESI UTARA")
    print("SUMBAR -> SUMATERA BARAT")
    print("SUMSEL -> SUMATERA SELATAN")
    print("SUMUT -> SUMATERA UTARA")
    print()
    print("# Kabupaten/Kota (generated from Kemendagri regencies.csv + common aliases)")
    
    # Common traditional/historical aliases not in CSV
    common_aliases = {
        "SOLO": "KOTA SURAKARTA",
        "MENADO": "KOTA MANADO",
        "DJAKARTA": "DKI JAKARTA",
        "DJAMBI": "KOTA JAMBI",
        "PALEMBANG KOTA": "KOTA PALEMBANG",
        "MAKASSAR KOTA": "KOTA MAKASSAR",
        "DENPASAR KOTA": "KOTA DENPASAR",
        "MALANG KOTA": "KOTA MALANG",
        "SEMARANG KOTA": "KOTA SEMARANG",
        "MEDAN KOTA": "KOTA MEDAN",
        "BATAM KOTA": "KOTA BATAM",
        "BALIKPAPAN KOTA": "KOTA BALIKPAPAN",
        "SAMARINDA KOTA": "KOTA SAMARINDA",
        "PONTIANAK KOTA": "KOTA PONTIANAK",
        "BANJARMASIN KOTA": "KOTA BANJARMASIN",
        "KUPANG KOTA": "KOTA KUPANG",
        "AMBON KOTA": "KOTA AMBON",
        "TERNATE KOTA": "KOTA TERNATE",
        "JAYAPURA KOTA": "KOTA JAYAPURA",
        "SORONG KOTA": "KOTA SORONG",
    }
    
    for alias, official in common_aliases.items():
        print(f"{alias} -> {official}")
    
    for alias in generate_aliases(csv_path):
        print(alias)

if __name__ == "__main__":
    main()