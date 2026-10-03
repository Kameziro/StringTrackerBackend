-- Cidade do clube: a plataforma cadastra o clube com nome e cidade, e o aluno descobre clubes pela cidade.
-- Nula porque os clubes criados antes da agenda (seed e nomes livres de jogos) não têm cidade.
ALTER TABLE clubs ADD COLUMN city_id BIGINT;
ALTER TABLE clubs ADD CONSTRAINT fk_clubs_city FOREIGN KEY (city_id) REFERENCES city (id);

CREATE INDEX idx_clubs_city_id ON clubs (city_id);
