ps -ef | grep alpino | grep -v grep | awk '{print $2}' | xargs kill
echo "Stopped Alpino server."

cd $SOLR_HOME
bin/solr stop
echo "Stopped Solr server."

ps -ef | grep lookup-service | grep -v grep | awk '{print $2}' | xargs kill
echo "Stopped lookup service."

ps -ef | grep fuseki-server | grep -v grep | awk '{print $2}' | xargs kill
echo "Stopped Fuseki server."
