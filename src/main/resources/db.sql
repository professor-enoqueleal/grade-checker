DROP TABLE IF EXISTS USR_SESSION;

DROP TABLE IF EXISTS MEMBERS;
DROP TABLE IF EXISTS GROUPS;

--CREATE TABLE USR_SESSION(ID INT PRIMARY KEY AUTO_INCREMENT, ACCESS_TOKEN VARCHAR(255));
CREATE TABLE GROUPS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), REPOSITORY_URL VARCHAR(255), REPOSITORY_OWNER VARCHAR(255), REPOSITORY_NAME VARCHAR(255));
CREATE TABLE MEMBERS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), GITHUB_USERNAME VARCHAR(255), GROUP_ID INT, FOREIGN KEY (GROUP_ID) REFERENCES GROUPS (ID));

--# TURMA B
-- INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('decimo-andar', 'https://github.com/EcthorSilva/decimo-andar', 'EcthorSilva', 'decimo-andar');
-- INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('ticketgol', 'https://github.com/MicaelCadete/ticketgol', 'MicaelCadete', 'ticketgol');
-- INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('mobile-genius', 'https://github.com/dianalinaemidio/mobile_genius', 'dianalinaemidio', 'mobile_genius');
-- INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('funcionalidades-projeto', 'https://github.com/pedroSantosSilva44/funcionalidades-projeto', 'pedroSantosSilva44', 'funcionalidades-projeto');
-- INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('imobiliaria', 'https://github.com/NatanSilva31/projeto-resort-imobiliaria', 'NatanSilva31', 'projeto-resort-imobiliaria');
-- INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('reservas-para-restaurantes', 'https://github.com/IgorPaes/reservas-para-restaurantes', 'IgorPaes', 'reservas-para-restaurantes');
-- INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('qualiset', 'https://github.com/EduardoXerxenevsky/qualiset', 'EduardoXerxenevsky', 'qualiset');

--# TURMA D
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('sistema-de-clinica-medica', 'https://github.com/RogerSouza1/sistema-de-clinica-medica', 'RogerSouza1', 'sistema-de-clinica-medica');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('sistema-de-vagas-tech-yks', 'https://github.com/Guihsp/sistema-de-vagas-tech-yks-pi-3', 'Guihsp', 'sistema-de-vagas-tech-yks-pi-3');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('PI-Pizzaria', 'https://github.com/Guilherme-Kaua-Silva/PI-Pizzaria', 'Guilherme-Kaua-Silva', 'PI-Pizzaria');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('veu-e-gravata', 'https://github.com/JoaoVictorMSouza/veu-e-gravata', 'JoaoVictorMSouza', 'veu-e-gravata');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('aluguel-de-imoveis', 'https://github.com/GustavoFernandesBatista/aluguel-de-imoveis', 'GustavoFernandesBatista', 'aluguel-de-imoveis');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('color-4-rit', 'https://github.com/GabrielSouza45/color-4-rit', 'GabrielSouza45', 'color-4-rit');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('Futebolize', 'https://github.com/Prophantasia/Futebolize', 'Prophantasia', 'Futebolize');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('date-of-love', 'https://github.com/MatheusRafaDev/date-of-love', 'MatheusRafaDev', 'date-of-love');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('La-Dolce-Vita', 'https://github.com/JoaoRodrigues10/La-Dolce-Vita', 'JoaoRodrigues10', 'La-Dolce-Vita');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('gerenciamento-musica', 'https://github.com/samuhkls/gerenciamento-musica', 'samuhkls', 'gerenciamento-musica');
