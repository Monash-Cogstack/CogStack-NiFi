# For some reason a small number of documents are not getting imported by

# out pipeline. We need to figure out why. Idea here is to

# extract the document IDs from elastic search and compare

# to those in the sqlserver



# possible packages - "elastic" https://www.r-bloggers.com/2017/08/elastic-elasticsearch-for-r/

# Our elastic search docker is localhost:9200 or 9600



library(elastic)

library(tidyverse)



ec <- elastic::connect(user="admin", pwd="admin")

ec$ping()



cat_indices(ec)



# index is source_data_v2_20220311

thisindex <- "source_data_v2_20220311"

index_get(conn=ec, index=thisindex)





p<-Search(conn=ec, index=thisindex, body='{"_source" : ["doc_id"]}')



# total available

p$hits$total



# What we have so far

p$hits$hits

map_dbl(p$hits$hits, ~as.numeric(.x$`_id`))



# Get everything with a scrolling search



getAll <- function(conn, indexname, size=10000) {

  res1 <- Search(conn=conn, index=indexname, body='{"_source" : ["doc_id"]}', time_scroll="1m", size=size)

  out1 <- map_dbl(res1$hits$hits, ~as.numeric(.x$`_id`))

  hits <- 1

  while (hits != 0) {

    tmp1 <- scroll(conn=conn, x = res1$`_scroll_id`, size=size)

    hits = length(tmp1$hits$hits)

    if (hits > 0) {

      out1 <- c(out1, map_dbl(tmp1$hits$hits, ~as.numeric(.x$`_id`)))

    }

  }

  return(out1)

}



h <- getAll(conn=ec, indexname=thisindex)

length(h)



successfuldocs <- tibble(docID = h)

save(successfuldocs, file="elasticsearchIDs.Rda")



## Now connect to sqlserver and see what didn't make it

library(odbc)

library(DBI)



conN <- dbConnect(odbc(),

                  driver = "ODBC Driver 17 for SQL Server",

                  server = "172.19.203.161",

                  database = "Workbench",

                  UID = "rbeare",

                  PWD = "Xw6&8-&TELC",

                  port = 1433)





cernerdocs <- tbl(conN, "RB_tblrecentblobswithdemographics")

missingdocs <- anti_join(cernerdocs, successfuldocs, by=c("EVENT_ID" = "docID"), copy=TRUE)



missingdocs <- collect(missingdocs)

missingdocs.nonempty <- filter(missingdocs, BLOB_LENGTH > 1)
