import os
import time
import random
import requests
from bs4 import BeautifulSoup
from concurrent.futures import ThreadPoolExecutor
import csv


BASE_URL = "https://papers.nips.cc"
STORAGE_DIRECTORY = "/Users/muhammadsarim/Desktop/NeurIPS_Papers-Python/"


def prepare_storage_directory():
    os.makedirs(STORAGE_DIRECTORY, exist_ok=True)


def fetch_and_process_papers_for_year(year, csv_writer):
    year_page_url = f"{BASE_URL}/paper_files/paper/{year}"
    year_folder = os.path.join(STORAGE_DIRECTORY, str(year))

    try:
        os.makedirs(year_folder, exist_ok=True)
        year_page = fetch_document_with_delay(year_page_url)

        paper_links = year_page.select("ul.paper-list li.conference a")
        if not paper_links:
            print(f"No papers found for year: {year}")
            return

        for paper_link in paper_links:
            paper_title = paper_link.text.strip()
            paper_page_url = BASE_URL + paper_link['href']
            process_paper(paper_title, paper_page_url, year_folder, year, csv_writer)

    except Exception as e:
        print(f"Error fetching papers for year: {year}")
        print(e)


def fetch_document_with_delay(url):
    try:
        time.sleep(1 + random.randint(0, 2))  # Simulating delay
        response = requests.get(url)
        response.raise_for_status()
        return BeautifulSoup(response.text, 'html.parser')
    except requests.exceptions.RequestException as e:
        print(f"Failed to fetch document: {url}")
        print(e)
        return None


def process_paper(paper_title, paper_page_url, year_folder, year, csv_writer):
    try:
        print(f"Processing paper: {paper_title}")
        paper_document = fetch_document_with_delay(paper_page_url)

        authors = extract_authors(paper_document)
        pdf_url = extract_pdf_url(paper_document)

        if pdf_url != "N/A":
            download_pdf(pdf_url, year_folder, paper_title)

        save_paper_details_to_csv(authors, pdf_url, paper_title, paper_page_url, year, csv_writer)

    except Exception as e:
        print(f"Error processing paper: {paper_title}")
        print(e)


def extract_authors(document):
    author_elements = document.select("i")
    authors = [author.text for author in author_elements]
    return "; ".join(authors)


def extract_pdf_url(document):
    pdf_element = document.select_one("a[href$=.pdf]")
    return BASE_URL + pdf_element['href'] if pdf_element else "N/A"


def download_pdf(pdf_url, save_path, title):
    sanitized_title = "".join(c if c.isalnum() else "_" for c in title) + ".pdf"
    file_path = os.path.join(save_path, sanitized_title)
    print(f"Downloading PDF: {pdf_url}")

    try:
        download_file(pdf_url, file_path)
    except Exception as e:
        print(f"Failed to download PDF: {pdf_url}")
        print(e)


def download_file(url, save_path):
    response = requests.get(url, stream=True)
    response.raise_for_status()
    
    with open(save_path, 'wb') as out_file:
        for chunk in response.iter_content(chunk_size=1024):
            if chunk:
                out_file.write(chunk)
    print(f"Downloaded: {save_path}")


def save_paper_details_to_csv(authors, pdf_url, paper_title, paper_page_url, year, csv_writer):
    try:
        csv_writer.writerow([year, paper_title, authors, paper_page_url, pdf_url])
        print(f"Processed paper: {paper_title}")
    except Exception as e:
        print(f"Error saving paper details for {paper_title}")
        print(e)


def main():
    try:
        prepare_storage_directory()
        with open(os.path.join(STORAGE_DIRECTORY, "papers_output.csv"), 'w', newline='', encoding='utf-8') as file:
            csv_writer = csv.writer(file)
            csv_writer.writerow(["Year", "Title", "Authors", "Paper URL", "PDF URL"])

            with ThreadPoolExecutor(max_workers=7) as executor:
                for year in range(2023, 2018, -1):
                    fetch_and_process_papers_for_year(year, csv_writer)

    except Exception as e:
        print("Error in main execution.")
        print(e)


if __name__ == "__main__":
    main()