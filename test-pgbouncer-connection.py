#!/usr/bin/env python3
"""
Test script to verify PgBouncer connections are working correctly
"""

import psycopg2
import sys

def test_connection(host, port, database, user, password, description):
    """Test a database connection and return success status and message"""
    try:
        print(f"Testing {description}...")
        conn = psycopg2.connect(
            host=host,
            port=port,
            database=database,
            user=user,
            password=password
        )
        
        cursor = conn.cursor()
        cursor.execute("SELECT version();")
        version = cursor.fetchone()[0]
        
        cursor.execute("SELECT current_database();")
        db_name = cursor.fetchone()[0]
        
        cursor.close()
        conn.close()
        
        print(f"  ✓ Success: Connected to {db_name} ({version})")
        return True
        
    except Exception as e:
        print(f"  ✗ Failed: {str(e)}")
        return False

def main():
    """Main test function"""
    print("PgBouncer Connection Test")
    print("=" * 30)
    
    # Connection parameters
    connections = [
        {
            "host": "localhost",
            "port": 6432,
            "database": "econ",
            "user": "game",
            "password": "gamepass",
            "description": "PgBouncer Master (Write)"
        },
        {
            "host": "localhost",
            "port": 6433,
            "database": "econ",
            "user": "game",
            "password": "gamepass",
            "description": "PgBouncer Replica 1 (Read)"
        },
        {
            "host": "localhost",
            "port": 6434,
            "database": "econ",
            "user": "game",
            "password": "gamepass",
            "description": "PgBouncer Replica 2 (Read)"
        }
    ]
    
    # Test each connection
    success_count = 0
    for conn_params in connections:
        if test_connection(**conn_params):
            success_count += 1
        print()
    
    # Summary
    print(f"Results: {success_count}/{len(connections)} connections successful")
    
    if success_count == len(connections):
        print("🎉 All PgBouncer connections are working correctly!")
        return 0
    else:
        print("❌ Some connections failed. Check the output above.")
        return 1

if __name__ == "__main__":
    sys.exit(main())