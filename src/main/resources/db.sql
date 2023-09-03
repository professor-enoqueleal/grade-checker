--DROP TABLE IF EXISTS USR_SESSION;
DROP TABLE IF EXISTS GROUPS;
DROP TABLE IF EXISTS MEMBERS;
DROP TABLE IF EXISTS USR_SESSION;

--CREATE TABLE USR_SESSION(ID INT PRIMARY KEY AUTO_INCREMENT, ACCESS_TOKEN VARCHAR(255));
CREATE TABLE GROUPS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), REPOSITORY_URL VARCHAR(255), REPOSITORY_OWNER VARCHAR(255), REPOSITORY_NAME VARCHAR(255));
CREATE TABLE MEMBERS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), GITHUB_USERNAME VARCHAR(255), GROUP_ID INT);

--# TURMA B
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('community-senac', 'https://github.com/Brenno55/community-senac', 'Brenno55', 'community-senac');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('Restaurante', 'https://github.com/denis-lemon/Restaurante', 'denis-lemon', 'Restaurante');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('PI-Gerenciamneto-de-escola', 'https://github.com/Tmpavezzi/PI-Gerenciamneto-de-escola', 'Tmpavezzi', 'PI-Gerenciamneto-de-escola');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('gerenciador_de_notas', 'https://github.com/Danmqest/gerenciador_de_notas', 'Danmqest', 'gerenciador_de_notas');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('classificados-de-carros', 'https://github.com/GuiMikami/classificados-de-carros', 'GuiMikami', 'classificados-de-carros');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('e-coffe', 'https://github.com/Guiisf/e-coffe', 'Guiisf', 'e-coffe');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('projetoacademiapi3', 'https://github.com/carllopessil/projetoacademiapi3', 'carllopessil', 'projetoacademiapi3');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('residup', 'https://github.com/CleitonRSilvaa/residup', 'CleitonRSilvaa', 'residup');

--# TURMA C
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('trip taker', 'https://github.com/Vinidsg/trip-taker', 'Vinidsg', 'trip-taker');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('genius kitchen', 'https://github.com/Rayane-Novaes/genius-kitchen', 'Rayane-Novaes', 'genius-kitchen');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('fluencynow', 'https://github.com/vilacalima/fluencynow', 'vilacalima', 'fluencynow');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('zé mudança', 'https://github.com/WWAraujo/ZeMudanca', 'WWAraujo', 'ZeMudanca');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('estética e cosmetologia', 'https://github.com/ManoelVi/Projeto-integrador-3', 'ManoelVi', 'Projeto-integrador-3');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('suplementos', 'https://github.com/Projeto-Senac-22-24/PI3', 'Projeto-Senac-22-24', 'PI3');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('divulgação de eventos', 'https://github.com/RafaelEtec/pi_git', 'RafaelEtec', 'pi_git');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('barista master', 'https://github.com/MAGuima/CafeteriaBaristaMaster', 'MAGuima', 'CafeteriaBaristaMaster');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('loja de Camisa', 'https://github.com/danielNevesSilva/Loja-de-Camisa', 'danielNevesSilva', 'Loja-de-Camisa');

--# TURMA D
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('unihub', 'https://github.com/Projeto-Integrador-3-SENAC/UNIHUB', 'Projeto-Integrador-3-SENAC', 'UNIHUB');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('from-earth', 'https://github.com/From-Earth/Reader', 'From-Earth', 'Reader');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('valorante', 'https://github.com/victormesquitta/PI-Valorant', 'victormesquitta', 'PI-Valorant');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('e-commerce', 'https://github.com/WEALVES/ProjetoPI3-Ecommerce', 'WEALVES', 'ProjetoPI3-Ecommerce');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('newpark', 'https://github.com/marcosgael/newpark', 'marcosgael', 'newpark');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('wiki music', 'https://github.com/NicollasBPSilva/WikiMusic', 'NicollasBPSilva', 'WikiMusic');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('password locker', 'https://github.com/maricsouza/PI-Password-Locker', 'maricsouza', 'PI-Password-Locker');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('aluguel de carros', 'https://github.com/Peregado/Projeto_Integrador', 'Peregado', 'Projeto_Integrador');
