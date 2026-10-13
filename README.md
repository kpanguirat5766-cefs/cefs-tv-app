# CEFS TV — application Android

Application Android de CEFS TV, la chaîne de l'apôtre Guirat KPAN (Centre d'Évangélisation et de Formation Sociale).

Elle affiche un écran d'accueil aux couleurs de CEFS TV, puis ouvre la chaîne en plein écran. Les vidéos passent en plein écran à l'horizontale. Les liens YouTube s'ouvrent dans l'application YouTube. Un écran « Pas de connexion » avec un bouton « Réessayer » s'affiche quand Internet manque.

## Obtenir l'APK

GitHub compile l'application tout seul à chaque modification :

1. Ouvrez l'onglet **Actions** du dépôt et attendez que la compilation soit verte (environ 5 minutes).
2. Ouvrez la section **Releases** et téléchargez **CEFS-TV.apk**.
3. Sur le téléphone Android, ouvrez le fichier et autorisez l'installation.

Le lien de la page Releases peut être envoyé aux fidèles pour qu'ils installent l'application.

## Changer le lien de la chaîne

Le lien ouvert par l'application se trouve dans `gradle.properties`, à la ligne `cefs.url=`.

Vous pouvez aussi le donner sans modifier de fichier : **Actions**, puis « Construire l'APK CEFS TV », puis **Run workflow**. Collez le lien public dans le champ prévu et lancez la compilation.

La page CEFS TV doit être partagée avec un lien public (menu **Partager** de la page). Sinon, l'application demandera une connexion à Claude.

## Mises à jour

Chaque nouvelle compilation porte un numéro plus élevé. Elle s'installe par-dessus l'ancienne version, car toutes les versions sont signées avec la même clé (`app/cefs-tv.jks`). Ne supprimez pas ce fichier : sans lui, les téléphones refuseront les mises à jour.

## Fichiers

- `app/src/main/java/org/cefs/tv/MainActivity.java` : le lecteur (accueil, plein écran, erreurs, bouton Retour).
- `app/src/main/res/` : icône, écran d'accueil, couleurs et textes.
- `.github/workflows/apk.yml` : la compilation automatique sur GitHub.
