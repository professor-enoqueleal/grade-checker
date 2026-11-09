-- Grade Checker schema and seed data (openspec capability database-bootstrap).
-- Runs at EVERY startup and MUST be idempotent: never DROP/DELETE/TRUNCATE.
--   * new tables: CREATE TABLE IF NOT EXISTS (in foreign key order);
--   * new columns: ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...;
--   * seeds: INSERT ... SELECT ... WHERE NOT EXISTS (never change existing rows).

--DROP TABLE IF EXISTS USR_SESSION;
--DROP TABLE IF EXISTS MEMBERS;
--DROP TABLE IF EXISTS CONTRIBUTORS;
--DROP TABLE IF EXISTS IDENTITY_ALIASES;
--DROP TABLE IF EXISTS GROUPS;

--CREATE TABLE USR_SESSION(ID INT PRIMARY KEY AUTO_INCREMENT, ACCESS_TOKEN VARCHAR(255));
CREATE TABLE IF NOT EXISTS GROUPS(
    ID INT PRIMARY KEY AUTO_INCREMENT,
    NAME VARCHAR(255),
    REPOSITORY_URL VARCHAR(255),
    REPOSITORY_OWNER VARCHAR(255),
    REPOSITORY_NAME VARCHAR(255),
    REPOSITORY_PRIVATE BOOLEAN DEFAULT FALSE
);

-- archived groups (openspec capability group-management): NULL = active. Archiving never deletes members or aliases,
-- and an archived group keeps its REPOSITORY_URL, so the seeds below do not insert it again
ALTER TABLE GROUPS ADD COLUMN IF NOT EXISTS ARCHIVED_AT TIMESTAMP;

-- members = students of the group, marked by an administrator (openspec capability group-members).
-- Contributors come from GitHub and are not stored: the old CONTRIBUTORS table is no longer created or used
-- (databases created by older versions keep it, since this script never drops tables). GITHUB_ID is written as NULL,
-- so the old foreign key MEMBERS.GITHUB_ID -> CONTRIBUTORS.ID of those databases is always satisfied.
CREATE TABLE IF NOT EXISTS MEMBERS(
    ID INT PRIMARY KEY AUTO_INCREMENT,
    NAME VARCHAR(255),
    GITHUB_USERNAME VARCHAR(255),
    GITHUB_ID INT,
    GROUP_ID INT,
    CREATED_MANUALLY BOOLEAN DEFAULT FALSE,
    CONTRIBUTIONS INT DEFAULT 0,
    FOREIGN KEY (GROUP_ID) REFERENCES GROUPS (ID),
    UNIQUE (GITHUB_USERNAME, GROUP_ID)
);

-- manual association of a git identity without account to a login of the group (openspec capability identity-aliases)
-- IDENTITY_KEY: "email:<normalized e-mail>" or "name:<normalized git name>"
CREATE TABLE IF NOT EXISTS IDENTITY_ALIASES(
    ID INT PRIMARY KEY AUTO_INCREMENT,
    GROUP_ID INT NOT NULL,
    IDENTITY_KEY VARCHAR(512) NOT NULL,
    LOGIN VARCHAR(255) NOT NULL,
    CREATED_BY VARCHAR(255),
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (GROUP_ID) REFERENCES GROUPS (ID),
    UNIQUE (GROUP_ID, IDENTITY_KEY)
);

--# | name | url | owner | repo
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('life-bf', 'https://github.com/fehhmont/LifeBF', 'fehhmont', 'LifeBF');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('sip-pair', 'https://github.com/affernanda/sip-pair.', 'affernanda', 'sip-pair.');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('goi-bankline', 'https://github.com/MarceloAfonso-dev/goi-bankline', 'MarceloAfonso-dev', 'goi-bankline');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('cine-tech', 'https://github.com/Roymp3/CineTech', 'Roymp3', 'cinetech');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('papo-de-viagem', 'https://github.com/BrenoMS117/Project-Papo-de-Viagem', 'BrenoMS117', 'Project-Papo-de-Viagem');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('strong-forge', 'https://github.com/K4u4z/strong-forge', 'K4u4z', 'strong-forge');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('estudio-tatto', 'https://github.com/erikal21/estudio-tatto', 'erikal21', 'estudio-tatto');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('your-coaching', 'https://github.com/leonardo10777/your-coaching', 'leonardo10777', 'your-coaching');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('art-vision', 'https://github.com/Erickss509E/Art-Vision', 'Erickss509E', 'Art-Vision');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('quotation', 'https://github.com/roMMarinho/Quotation', 'roMMarinho', 'Quotation');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('little-market', 'https://github.com/PIbnz/little-market', 'PIbnz', 'little-market');

-- seed groups: inserted only when no group has the same REPOSITORY_URL
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'projeto-chamados', 'https://github.com/Saacbs/Projeto-Chamados', 'Saacbs', 'projeto-chamados' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/Saacbs/Projeto-Chamados');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'sensei-dev', 'https://github.com/VitorConrado04/sensei-dev', 'VitorConrado04', 'sensei-dev' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/VitorConrado04/sensei-dev');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'clinica', 'https://github.com/Hugodev0711/clinica-pi', 'Hugodev0711', 'clinica-pi' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/Hugodev0711/clinica-pi');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'nonna-pizza', 'https://github.com/rulckus/nonna-pizza', 'rulckus', 'nonna-pizza' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/rulckus/nonna-pizza');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'conversor-estoque-xml', 'https://github.com/GuilhermeMeloGS/conversor-estoque-xml', 'GuilhermeMeloGS', 'conversor-estoque-xml' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/GuilhermeMeloGS/conversor-estoque-xml');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'cannamed', 'https://github.com/LucasMoura-dev777/cannamed', 'LucasMoura-dev777', 'cannamed' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/LucasMoura-dev777/cannamed');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'sistema-gestao-funcionarios', 'https://github.com/Renan7Erick/sistema-gestao-funcionarios', 'Renan7Erick', 'sistema-gestao-funcionarios' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/Renan7Erick/sistema-gestao-funcionarios');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'locacaoquadras', 'https://github.com/pedfaroh-bit/locacaoquadras', 'pedfaroh-bit', 'locacaoquadras' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/pedfaroh-bit/locacaoquadras');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'plataforma-amis', 'https://github.com/amanda-pedreira/Plataforma-Amis', 'amanda-pedreira', 'plataforma-amis' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/amanda-pedreira/Plataforma-Amis');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'reelbook', 'https://github.com/itscuds/reelbook', 'itscuds', 'reelbook' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/itscuds/reelbook');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'biblioteca-virtual', 'https://github.com/Melibanio/biblioteca-virtual', 'Melibanio', 'biblioteca-virtual' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/Melibanio/biblioteca-virtual');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'livraria-lexxis', 'https://github.com/FelipeZx405/livraria-lexxis', 'FelipeZx405', 'livraria-lexxis' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/FelipeZx405/livraria-lexxis');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) SELECT 'eventhub', 'https://github.com/gabrielrgtx/eventhub', 'gabrielrgtx', 'eventhub' WHERE NOT EXISTS (SELECT 1 FROM GROUPS WHERE REPOSITORY_URL = 'https://github.com/gabrielrgtx/eventhub');
