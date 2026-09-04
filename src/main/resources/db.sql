DROP TABLE IF EXISTS USR_SESSION;

DROP TABLE IF EXISTS MEMBERS;
DROP TABLE IF EXISTS CONTRIBUTORS;
DROP TABLE IF EXISTS GROUPS;

--CREATE TABLE USR_SESSION(ID INT PRIMARY KEY AUTO_INCREMENT, ACCESS_TOKEN VARCHAR(255));
CREATE TABLE GROUPS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), REPOSITORY_URL VARCHAR(255), REPOSITORY_OWNER VARCHAR(255), REPOSITORY_NAME VARCHAR(255), REPOSITORY_PRIVATE BOOLEAN DEFAULT FALSE);
CREATE TABLE MEMBERS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), GITHUB_USERNAME VARCHAR(255), GROUP_ID INT, FOREIGN KEY (GROUP_ID) REFERENCES GROUPS (ID));
CREATE TABLE CONTRIBUTORS(ID INT, LOGIN VARCHAR(255), AVATAR_URL VARCHAR(255), HTML_URL VARCHAR(255), CONTRIBUTIONS INT, GROUP_ID INT, FOREIGN KEY (GROUP_ID) REFERENCES GROUPS (ID));

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

INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('projeto-chamados', 'https://github.com/Saacbs/Projeto-Chamados', 'Saacbs', 'projeto-chamados');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('clinica', 'https://github.com/Hugodev0711/clinica-pi', 'Hugodev0711', 'clinica-pi');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('sensei-dev', 'https://github.com/VitorConrado04/sensei-dev', 'VitorConrado04', 'sensei-dev');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('conversor-estoque-xml', 'https://github.com/GuilhermeMeloGS/conversor-estoque-xml', 'GuilhermeMeloGS', 'conversor-estoque-xml');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('sistema-gestao-funcionarios', 'https://github.com/Renan7Erick/sistema-gestao-funcionarios', 'Renan7Erick', 'sistema-gestao-funcionarios');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('reelbook', 'https://github.com/itscuds/reelbook', 'itscuds', 'reelbook');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('biblioteca-virtual', 'https://github.com/Melibanio/biblioteca-virtual', 'Melibanio', 'biblioteca-virtual');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('livraria-lexxis', 'https://github.com/FelipeZx405/livraria-lexxis', 'FelipeZx405', 'livraria-lexxis');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('eventhub', 'https://github.com/gabrielrgtx/eventhub', 'gabrielrgtx', 'eventhub');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('grupologistica', 'https://github.com/pedfaroh-bit/grupologistica', 'pedfaroh-bit', 'grupologistica');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('Nonna-Pizza', 'https://github.com/yGoomez/Nonna-Pizza', 'yGoomez', 'Nonna-Pizza');
