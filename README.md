# Middleware d'Orchestration de Calculs Distribués (Java RMI + Sockets TCP)

Plateforme Master/Worker qui distribue un traitement d'image (luminosité, niveaux de gris, négatif, sépia)
sur plusieurs machines. Chaque image est découpée en bandes de lignes ; chaque bande est une **tâche**.

## Rôle de chaque technologie
| Techno | Utilisation dans le code |
|---|---|
| **Java RMI** | `registerWorker()` (enregistrement dynamique), `heartbeat()`, `getSnapshot()` – package `common` (interfaces) |
| **Sockets TCP** | Client → Master (image), Master → Worker (bandes de pixels), retour des résultats |
| **Load Balancing** | `master/LoadBalancer` : *Least Loaded Worker* (charge = tâches en cours / capacité) |
| **Heartbeat** | Worker : signal toutes les 3 s. Master (`HeartbeatManager`) : > 10 s sans signal ⇒ `FAILED` |
| **Tolérance aux pannes** | tâches du Worker en panne remises en file (`REASSIGN`), max 5 essais ; pas de doublon (`Task.done`) |
| **Dashboard** | `master/DashboardFrame` (Swing) ou `ConsoleDashboard` (sans écran) |

## Cycle de fonctionnement
1. Master démarre (registre RMI :1099, écoute clients TCP :5000).
2. Les Workers s'enregistrent par RMI (`registerWorker`) et envoient des heartbeats.
3. Le Client envoie l'image au Master (TCP).
4. Le Master découpe en tâches, le LoadBalancer choisit le Worker le moins chargé.
5. Le Master envoie la bande au Worker (TCP) ; le Worker calcule et répond sur la même connexion.
6. Si un Worker tombe (erreur TCP ou heartbeat perdu) → `FAILED`, ses tâches sont redistribuées.
7. Le Master assemble les bandes et renvoie l'image finale au Client (+ vérification côté client).

## Structure
```
src/common   MasterInterface, WorkerInterface, Task, TaskResult, Snapshot, FilterType, Net, Constants
src/master   MasterImpl, WorkerManager, WorkerState, LoadBalancer, HeartbeatManager,
             TaskScheduler, Job, ClientListener, DashboardFrame, ConsoleDashboard, MasterMain
src/worker   WorkerImpl, TaskServer (TCP), TaskExecutor, WorkerMain
src/client   ClientMain
```

## Lancer
```bash
./build.sh                      # compile (JDK 11+)
./demo.sh                       # démo automatique locale avec panne d'un Worker
```
Manuellement (un terminal chacun) :
```bash
./run-master.sh                                   # ouvre le Dashboard Swing (ou --console)
./run-worker.sh --id=W1 --threads=2
./run-worker.sh --id=W2 --threads=2
./run-worker.sh --id=W3 --threads=2
./run-client.sh localhost photo.jpg resultat.png BRIGHTNESS 60 500
./run-client.sh localhost gen:3000x2000 resultat.png SEPIA      # image de test générée
```
Arguments client : `<hôte> <image|gen:LxH> <sortie.png> <FILTRE> [param] [delayMs] [chunkRows]`
(`delayMs` simule un calcul lourd par tâche pour mieux voir la répartition pendant la démo.)

## Plusieurs machines (vraie soutenance)
- Master : `./run-master.sh --host=<IP_MASTER>`
- Worker : `./run-worker.sh <IP_MASTER> --host=<IP_DU_WORKER> --id=W1`
- Ouvrir les ports 1099, 5000 et les ports TCP des Workers (fixer avec `--port=6001`, etc.).

## Dashboard web : « Nouvelle tâche » (nouveau)
Au démarrage, le Master lance aussi un serveur HTTP (Java pur, port **8080**) et ouvre le navigateur :
**http://localhost:8080** (depuis un autre PC : `http://IP_DU_MASTER:8080`).

1. **Parcourir…** : choisir l'image (elle est réellement *uploadée* au Master, pas juste son chemin).
2. Choisir le **filtre** et les **paramètres** (luminosité, délai par tâche, lignes par bloc, nom du fichier de sortie).
3. **Exécuter** : barre de progression (tâches terminées / total), image originale et résultat côte à côte,
   bouton de téléchargement. Le résultat est aussi enregistré dans `results/<nom>.png` sur la machine du Master.
4. À droite : Workers (ONLINE / BUSY / FAILED, charge, heartbeat), compteurs, historique.

```
Navigateur --HTTP upload--> WebServer (Master) --> Job --> LoadBalancer --TCP--> Workers
      ^                                                                              |
      +----------- progression + résultat (HTTP) <------ assemblage <---- résultats TCP
```
Endpoints : `GET /` · `GET /api/status` · `POST /api/jobs?filter=&param=&delay=&chunk=&name=` (corps = fichier image)
· `GET /api/jobs/{id}` · `GET /api/jobs/{id}/result`.
Le fichier `web/index.html` doit rester dans le dossier du projet : **lancer le Master depuis la racine du projet**.
Port modifiable avec `-Dweb.port=9090`. L'ancien client en ligne de commande et la fenêtre Swing fonctionnent toujours.
Pas d'authentification : à utiliser sur un réseau de confiance (salle de TP).

## Démonstration de panne (soutenance)
Lancer un job avec `delayMs` élevé (ex. 2000), puis **tuer un Worker** (Ctrl+C ou `kill -9`) :
le Dashboard passe le Worker en `FAILED`, les tâches sont réassignées, le job se termine quand même.
Le Worker relancé avec le même `--id` repasse `ONLINE`. Option `--crash-after=N` : panne automatique après N tâches.
Pour accélérer les tests : `-Dhb.interval=1000 -Dhb.timeout=4000`.
